package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServiceProbeTests {

    private static HttpServer server;
    private static final AtomicInteger statusCode = new AtomicInteger(200);
    private static final AtomicReference<String> body = new AtomicReference<>("{\"component\":\"orders-api\"}");

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode.get(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsOkAndProblemErrorBodiesUpToTheLimit() {
        ServiceProbe probe = new ServiceProbe();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        try {
            statusCode.set(200);
            body.set("{\"component\":\"gateway-api\",\"downstream\":{\"component\":\"orders-api\"}}");
            assertThat(probe.get(uri)).isEqualTo(body.get());

            // Non-2xx problem+json from a failed downstream call is preserved as evidence.
            statusCode.set(504);
            body.set("{\"status\":504,\"detail\":\"Downstream service orders-api timed out\"}");
            assertThat(probe.get(uri)).isEqualTo(body.get());

            // Empty non-2xx pages still read as absent.
            statusCode.set(502);
            body.set("   ");
            assertThat(probe.get(uri)).isNull();

            // Nested chains larger than 1000 bytes are kept in full up to MAX_RESPONSE_BYTES.
            statusCode.set(200);
            String nested = "x".repeat(4000);
            body.set(nested);
            assertThat(probe.get(uri)).hasSize(4000);

            body.set("y".repeat(ServiceProbe.MAX_RESPONSE_BYTES + 500));
            assertThat(probe.get(uri)).hasSize(ServiceProbe.MAX_RESPONSE_BYTES);
        } finally {
            probe.close();
        }
    }

    @Test
    void returnsNullWhenTheServiceCannotBeReached() {
        ServiceProbe probe = new ServiceProbe();
        try {
            assertThat(probe.get(URI.create("http://127.0.0.1:1/"))).isNull();
        } finally {
            probe.close();
        }
    }
}
