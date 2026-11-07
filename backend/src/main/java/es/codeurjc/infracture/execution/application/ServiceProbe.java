package es.codeurjc.infracture.execution.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

// Asks a running service for its real response, the evidence that it works.
// The name of the service only resolves once the backend has joined the
// execution network, which happens when the backend runs in a container.
@Component
public class ServiceProbe {

    private static final int MAX_RESPONSE_LENGTH = 1000;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    // Returns null when the service cannot be reached or does not answer 2xx.
    public String get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            String body = response.body();
            return body.length() > MAX_RESPONSE_LENGTH ? body.substring(0, MAX_RESPONSE_LENGTH) : body;
        } catch (IOException _) {
            return null;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
