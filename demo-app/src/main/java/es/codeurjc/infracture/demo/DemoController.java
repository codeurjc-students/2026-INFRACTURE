package es.codeurjc.infracture.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    private final DemoResponse response;

    public DemoController(
            @Value("${APP_ROLE}") String role,
            @Value("${APP_MODE}") String mode,
            @Value("${COMPONENT_NAME}") String component,
            @Value("${RESPONSE_DELAY_MS:0}") int responseDelayMs) {
        if (!"http".equals(role) || !"STATELESS".equals(mode)) {
            throw new IllegalStateException(
                    "Unsupported APP_ROLE=%s APP_MODE=%s; this image supports http/STATELESS".formatted(role, mode));
        }
        if (responseDelayMs < 0) {
            throw new IllegalStateException("RESPONSE_DELAY_MS must not be negative");
        }
        this.response = new DemoResponse(component, role, mode, responseDelayMs);
    }

    @GetMapping("/")
    public DemoResponse answer() throws InterruptedException {
        Thread.sleep(response.responseDelayMs());
        return response;
    }

    public record DemoResponse(String component, String role, String mode, int responseDelayMs) {
    }
}
