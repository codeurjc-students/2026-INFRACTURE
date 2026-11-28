package es.codeurjc.infracture.execution.application;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

// Asks a running service for its real response, the evidence that it works.
// The name of the service only resolves once the backend has joined the
// execution network, which happens when the backend runs in a container.
@Component
public class ServiceProbe {

    static final int MAX_RESPONSE_BYTES = 16_384;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @PreDestroy
    void close() {
        client.close();
    }

    // Returns the response body up to MAX_RESPONSE_BYTES, including non-2xx error bodies (such as 502/504
    // problem+json from a downstream chain), or null when the service cannot be reached or answered blank on error.
    public String get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                String text = new String(body.readNBytes(MAX_RESPONSE_BYTES), StandardCharsets.UTF_8);
                if (response.statusCode() / 100 != 2 && text.isBlank()) {
                    return null;
                }
                return text;
            }
        } catch (IOException _) {
            return null;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
