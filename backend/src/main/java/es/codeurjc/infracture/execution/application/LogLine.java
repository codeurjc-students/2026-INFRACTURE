package es.codeurjc.infracture.execution.application;

import java.time.Instant;
import java.util.UUID;

// One line a node of the plan, or its load when node is null, printed, with the time Docker recorded it.
public record LogLine(UUID node, Instant at, String text) {
}
