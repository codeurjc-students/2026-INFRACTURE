package es.codeurjc.infracture.execution.api;

import java.util.List;
import java.util.UUID;

// A node as it was frozen for the execution, with the limits its container ran with and the counters it declares.
public record MetricComponentDTO(UUID key, String name, Double cpuLimitPercent, Long memoryLimitBytes,
        List<String> counters) {
}
