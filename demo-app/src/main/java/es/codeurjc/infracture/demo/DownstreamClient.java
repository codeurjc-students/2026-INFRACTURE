package es.codeurjc.infracture.demo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Calls the configured downstream HTTP service for DOWNSTREAM_HTTP. Only GET DOWNSTREAM_URL is issued,
// with fixed connect/request timeouts, no redirects and a bounded response body.
@Component
@ConditionalOnProperty(name = "APP_MODE", havingValue = "DOWNSTREAM_HTTP")
public class DownstreamClient {

    static final int MAX_BODY_BYTES = 16_384;

    private final URI uri;
    private final String downstreamName;
    private final String downstreamKey;
    private final Duration requestTimeout;
    private final HttpClient client;
    private final JsonMapper jsonMapper;
    private final Counter calls;
    private final Counter failures;
    private final Counter timeouts;

    public DownstreamClient(
            @Value("${DOWNSTREAM_URL}") String url,
            @Value("${DOWNSTREAM_NAME:downstream}") String downstreamName,
            @Value("${DOWNSTREAM_KEY:}") String downstreamKey,
            @Value("${infracture.downstream.connect-timeout:1s}") Duration connectTimeout,
            @Value("${infracture.downstream.request-timeout:3s}") Duration requestTimeout,
            JsonMapper jsonMapper,
            MeterRegistry registry) {
        this.uri = parseUri(url);
        this.downstreamName = downstreamName.isBlank() ? "downstream" : downstreamName;
        this.downstreamKey = downstreamKey == null ? "" : downstreamKey.strip();
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.jsonMapper = jsonMapper;
        this.calls = Counter.builder("infracture.downstream.calls").register(registry);
        this.failures = Counter.builder("infracture.downstream.failures").register(registry);
        this.timeouts = Counter.builder("infracture.downstream.timeouts").register(registry);
    }

    @PreDestroy
    void close() {
        client.close();
    }

    public JsonNode call() {
        calls.increment();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(requestTimeout).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException ex) {
            timeouts.increment();
            throw timeoutProblem(null, ex);
        } catch (IOException ex) {
            failures.increment();
            throw failureProblem("Downstream service %s could not be reached".formatted(downstreamName), null, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failures.increment();
            throw failureProblem("Downstream service %s call was interrupted".formatted(downstreamName), null, ex);
        }

        byte[] bytes = readBounded(response);
        int status = response.statusCode();
        if (status == HttpStatus.GATEWAY_TIMEOUT.value()) {
            timeouts.increment();
            throw timeoutProblem(tryParseJson(bytes), null);
        }
        if (status / 100 != 2) {
            failures.increment();
            throw failureProblem("Downstream service %s failed with status %d".formatted(downstreamName, status),
                    tryParseJson(bytes), null);
        }
        if (bytes.length == 0) {
            failures.increment();
            throw failureProblem("Downstream service %s returned an empty response".formatted(downstreamName), null,
                    null);
        }
        try {
            return jsonMapper.readTree(bytes);
        } catch (JacksonException ex) {
            failures.increment();
            throw failureProblem("Downstream service %s returned an invalid JSON response".formatted(downstreamName),
                    null, ex);
        }
    }

    private byte[] readBounded(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            byte[] data = body.readNBytes(MAX_BODY_BYTES + 1);
            if (data.length > MAX_BODY_BYTES) {
                failures.increment();
                throw failureProblem(
                        "Downstream service %s response exceeded %d bytes".formatted(downstreamName, MAX_BODY_BYTES),
                        null, null);
            }
            return data;
        } catch (HttpTimeoutException ex) {
            timeouts.increment();
            throw timeoutProblem(null, ex);
        } catch (IOException ex) {
            failures.increment();
            throw failureProblem("Downstream service %s response could not be read".formatted(downstreamName), null,
                    ex);
        }
    }

    private JsonNode tryParseJson(byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }
        try {
            return jsonMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (JacksonException _) {
            return null;
        }
    }

    private ErrorResponseException timeoutProblem(JsonNode cause, Throwable exception) {
        return problem(HttpStatus.GATEWAY_TIMEOUT, "Downstream service %s timed out".formatted(downstreamName), cause,
                exception);
    }

    private ErrorResponseException failureProblem(String detail, JsonNode cause, Throwable exception) {
        return problem(HttpStatus.BAD_GATEWAY, detail, cause, exception);
    }

    private ErrorResponseException problem(HttpStatus status, String detail, JsonNode cause, Throwable exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("downstream", downstreamName);
        if (!downstreamKey.isEmpty()) {
            problem.setProperty("downstreamKey", downstreamKey);
        }
        if (cause != null) {
            problem.setProperty("cause", cause);
        }
        return new ErrorResponseException(status, problem, exception);
    }

    private static URI parseUri(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("DOWNSTREAM_URL is required for DOWNSTREAM_HTTP");
        }
        URI parsed;
        try {
            parsed = URI.create(url.strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("DOWNSTREAM_URL must be a valid HTTP URI: " + url, ex);
        }
        String scheme = parsed.getScheme();
        if (!parsed.isAbsolute() || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || parsed.getHost() == null || parsed.getHost().isBlank()) {
            throw new IllegalStateException("DOWNSTREAM_URL must be an absolute http:// or https:// URI: " + url);
        }
        return parsed;
    }
}
