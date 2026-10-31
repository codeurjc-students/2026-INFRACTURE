package es.codeurjc.infracture.project.domain;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import java.util.UUID;

public record ComponentDraft(
        UUID key,
        String templateKey,
        String name,
        double positionX,
        double positionY,
        ComponentConfiguration configuration) {
}
