package es.codeurjc.infracture.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// The load applied to the scenario (ADR 0012). It is a setting of the scenario, not a node: an empty target
// keeps the load settings until the user picks a new target.
@Embeddable
public record Workload(
        @Column(name = "target_key") UUID targetKey,
        String profile,
        @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) Map<String, Integer> parameters,
        @Column(nullable = false) int seed) {

    public Workload {
        parameters = Map.copyOf(parameters);
    }
}
