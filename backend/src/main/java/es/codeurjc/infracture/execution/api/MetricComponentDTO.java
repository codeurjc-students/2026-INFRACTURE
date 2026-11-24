package es.codeurjc.infracture.execution.api;

import java.util.UUID;

// A node as it was frozen for the execution, with the limits its container ran with.
public record MetricComponentDTO(UUID key, String name, Double cpuLimitPercent, Long memoryLimitBytes) {
}
