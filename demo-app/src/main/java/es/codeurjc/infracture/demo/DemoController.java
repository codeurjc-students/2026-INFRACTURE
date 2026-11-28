package es.codeurjc.infracture.demo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class DemoController {

    private static final Set<String> MODES = Set.of("STATELESS", "DATABASE_CRUD", "DOWNSTREAM_HTTP");

    private final String component;
    private final String role;
    private final String mode;
    private final int responseDelayMs;
    private final Optional<ItemStore> items;
    private final Optional<DownstreamClient> downstream;

    public DemoController(
            @Value("${APP_ROLE}") String role,
            @Value("${APP_MODE}") String mode,
            @Value("${COMPONENT_NAME}") String component,
            @Value("${RESPONSE_DELAY_MS:0}") int responseDelayMs,
            Optional<ItemStore> items,
            Optional<DownstreamClient> downstream) {
        if (!"http".equals(role) || !MODES.contains(mode)) {
            throw new IllegalStateException("Unsupported APP_ROLE=%s APP_MODE=%s; this image supports http with %s"
                    .formatted(role, mode, String.join(" or ", MODES.stream().sorted().toList())));
        }
        if (responseDelayMs < 0) {
            throw new IllegalStateException("RESPONSE_DELAY_MS must not be negative");
        }
        this.component = component;
        this.role = role;
        this.mode = mode;
        this.responseDelayMs = responseDelayMs;
        this.items = items;
        this.downstream = downstream;
    }

    // With a database, the answer counts its items: every request is a real read.
    // With a downstream service, the answer nests what that service replied.
    @GetMapping("/")
    public DemoResponse answer() {
        return new DemoResponse(component, role, mode, responseDelayMs,
                items.map(ItemStore::count).orElse(null),
                downstream.map(DownstreamClient::call).orElse(null));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DemoResponse(String component, String role, String mode, int responseDelayMs, Long items,
            JsonNode downstream) {
    }
}
