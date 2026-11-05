package es.codeurjc.infracture.execution.api;

import jakarta.validation.constraints.NotNull;

// The revision the client saved just before pressing Run: only that revision may run (ADR 0004).
public record RunExecutionDTO(@NotNull Integer revision) {
}
