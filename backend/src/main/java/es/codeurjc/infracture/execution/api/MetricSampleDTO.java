package es.codeurjc.infracture.execution.api;

import java.time.Instant;
import java.util.UUID;

// A null value means there was no reading at that moment; it is not zero.
public record MetricSampleDTO(long id, UUID componentKey, Instant at, Double cpuPercent, Long memoryBytes) {
}
