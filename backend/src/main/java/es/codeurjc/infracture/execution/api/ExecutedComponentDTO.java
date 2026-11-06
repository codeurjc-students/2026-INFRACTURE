package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.application.ComponentStatus;
import java.util.UUID;

// status and response are null until the backend has seen the running node.
public record ExecutedComponentDTO(UUID key, String name, ComponentType type, ComponentStatus status,
        String response) {
}
