package es.codeurjc.infracture.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

// An arrow from a port of the source node to the target node: the source depends on the target (ADR 0011).
// The three values are its identity, so it needs no key of its own.
@Embeddable
public record Connection(
        @Column(name = "source_key", nullable = false) UUID sourceKey,
        @Column(nullable = false) String port,
        @Column(name = "target_key", nullable = false) UUID targetKey) {
}
