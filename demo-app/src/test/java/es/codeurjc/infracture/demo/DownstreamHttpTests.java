package es.codeurjc.infracture.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "APP_ROLE=http",
        "APP_MODE=DOWNSTREAM_HTTP",
        "COMPONENT_NAME=gateway-api",
        "RESPONSE_DELAY_MS=0",
        "DOWNSTREAM_NAME=orders-api",
        "DOWNSTREAM_KEY=3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10",
        "infracture.downstream.connect-timeout=200ms",
        "infracture.downstream.request-timeout=250ms" })
@AutoConfigureMockMvc
@AutoConfigureMetrics
class DownstreamHttpTests {

    private static HttpServer downstream;
    private static final AtomicReference<Handler> behavior = new AtomicReference<>();

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    @BeforeAll
    static void startDownstream() throws IOException {
        downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstream.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        downstream.createContext("/", exchange -> behavior.get().handle(exchange));
        downstream.start();
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop(0);
    }

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("DOWNSTREAM_URL", () -> "http://127.0.0.1:" + downstream.getAddress().getPort() + "/");
    }

    @BeforeEach
    void resetBehavior() {
        respond(200, "application/json",
                "{\"component\":\"orders-api\",\"role\":\"http\",\"mode\":\"DATABASE_CRUD\",\"responseDelayMs\":0,\"items\":100}");
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void composesItsAnswerWithTheDownstreamResponseWithoutActingAsAProxy() throws Exception {
        AtomicReference<String> seenUri = new AtomicReference<>();
        AtomicReference<String> seenHeader = new AtomicReference<>();
        behavior.set(exchange -> {
            seenUri.set(exchange.getRequestURI().toString());
            seenHeader.set(exchange.getRequestHeaders().getFirst("X-Custom"));
            write(exchange, 200, "application/json",
                    "{\"component\":\"orders-api\",\"role\":\"http\",\"mode\":\"STATELESS\",\"responseDelayMs\":5}");
        });

        mvc.perform(get("/").queryParam("ignored", "1").header("X-Custom", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.component").value("gateway-api"))
                .andExpect(jsonPath("$.role").value("http"))
                .andExpect(jsonPath("$.mode").value("DOWNSTREAM_HTTP"))
                .andExpect(jsonPath("$.downstream.component").value("orders-api"))
                .andExpect(jsonPath("$.downstream.mode").value("STATELESS"))
                .andExpect(jsonPath("$.downstream.responseDelayMs").value(5));

        assertThat(seenUri.get()).isEqualTo("/");
        assertThat(seenHeader.get()).isNull();
        mvc.perform(get("/items/1")).andExpect(status().isNotFound());
    }

    @Test
    void returns502WithProblemDetailsAndNestedCauseWhenDownstreamFails() throws Exception {
        respond(500, "application/problem+json",
                "{\"title\":\"Internal Server Error\",\"status\":500,\"detail\":\"boom\"}");

        mvc.perform(get("/"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value("Downstream service orders-api failed with status 500"))
                .andExpect(jsonPath("$.downstream").value("orders-api"))
                .andExpect(jsonPath("$.downstreamKey").value("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10"))
                .andExpect(jsonPath("$.cause.detail").value("boom"));
    }

    @Test
    void doesNotFollowRedirectsAndRejectsInvalidOrOversizedResponsesWith502() throws Exception {
        behavior.set(exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + downstream.getAddress().getPort() + "/");
            write(exchange, 302, "text/plain", "");
        });
        mvc.perform(get("/")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Downstream service orders-api failed with status 302"));

        respond(200, "text/plain", "not-json");
        mvc.perform(get("/")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Downstream service orders-api returned an invalid JSON response"));

        String oversized = "{\"data\":\"" + "a".repeat(DownstreamClient.MAX_BODY_BYTES + 10) + "\"}";
        respond(200, "application/json", oversized);
        mvc.perform(get("/")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        "Downstream service orders-api response exceeded " + DownstreamClient.MAX_BODY_BYTES + " bytes"));
    }

    @Test
    void returns504WhenDownstreamTimesOutOrAnswers504() throws Exception {
        behavior.set(exchange -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            write(exchange, 200, "application/json", "{\"component\":\"orders-api\"}");
        });

        mvc.perform(get("/"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.detail").value("Downstream service orders-api timed out"))
                .andExpect(jsonPath("$.downstream").value("orders-api"))
                .andExpect(jsonPath("$.downstreamKey").value("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10"));

        respond(504, "application/problem+json",
                "{\"status\":504,\"detail\":\"Downstream service billing-api timed out\",\"downstream\":\"billing-api\"}");
        mvc.perform(get("/"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.downstream").value("orders-api"))
                .andExpect(jsonPath("$.cause.downstream").value("billing-api"));
    }

    @Test
    void countsDownstreamCallsFailuresAndTimeoutsAtMetrics() throws Exception {
        String before = scrape();
        double callsBefore = PrometheusText.counter(before, "infracture_downstream_calls_total").orElseThrow();
        double failuresBefore = PrometheusText.counter(before, "infracture_downstream_failures_total").orElseThrow();
        double timeoutsBefore = PrometheusText.counter(before, "infracture_downstream_timeouts_total").orElseThrow();

        mvc.perform(get("/")).andExpect(status().isOk());
        respond(503, "text/plain", "unavailable");
        mvc.perform(get("/")).andExpect(status().isBadGateway());
        respond(504, "application/problem+json", "{\"status\":504}");
        mvc.perform(get("/")).andExpect(status().isGatewayTimeout());

        String after = scrape();
        assertThat(PrometheusText.counter(after, "infracture_downstream_calls_total")).hasValue(callsBefore + 3.0);
        assertThat(PrometheusText.counter(after, "infracture_downstream_failures_total"))
                .hasValue(failuresBefore + 1.0);
        assertThat(PrometheusText.counter(after, "infracture_downstream_timeouts_total"))
                .hasValue(timeoutsBefore + 1.0);
    }

    @Test
    void rejectsAnInvalidOrMissingDownstreamUrl() {
        JsonMapper mapper = JsonMapper.builder().build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Duration timeout = Duration.ofSeconds(1);

        assertThatIllegalStateException().isThrownBy(
                () -> new DownstreamClient("", "orders", "", timeout, timeout, mapper, registry));
        assertThatIllegalStateException().isThrownBy(
                () -> new DownstreamClient("ftp://orders:8080/", "orders", "", timeout, timeout, mapper, registry));
        assertThatIllegalStateException().isThrownBy(
                () -> new DownstreamClient("/relative", "orders", "", timeout, timeout, mapper, registry));
    }

    private String scrape() throws Exception {
        return mvc.perform(get("/metrics")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static void respond(int status, String contentType, String body) {
        behavior.set(exchange -> write(exchange, status, contentType, body));
    }

    private static void write(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
