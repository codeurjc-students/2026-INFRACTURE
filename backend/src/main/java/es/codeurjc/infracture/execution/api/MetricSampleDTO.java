package es.codeurjc.infracture.execution.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// A null value or an absent counter means there was no reading at that moment; it is not zero.
public record MetricSampleDTO(long id, UUID componentKey, Instant at, Double cpuPercent, Long memoryBytes,
        Map<String, Double> counters) {
}
