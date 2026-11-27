package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.application.ExecutionMetricsService.MetricsView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// One page of the series of every node of an execution, with where the values come from and how to read them.
public record ExecutionMetricsDTO(
        String source,
        String cpuUnit,
        String memoryUnit,
        long intervalSeconds,
        boolean capturing,
        boolean hasMore,
        long nextAfter,
        List<MetricComponentDTO> components,
        List<MetricSampleDTO> samples) {

    static ExecutionMetricsDTO of(MetricsView view) {
        Map<Long, UUID> keys = view.nodes().stream()
                .collect(Collectors.toMap(node -> node.component().getId(), node -> node.component().getKey()));
        return new ExecutionMetricsDTO("DOCKER_STATS", "PERCENT_OF_ONE_CORE", "BYTES", view.intervalSeconds(),
                view.capturing(), view.hasMore(), view.nextAfter(),
                view.nodes().stream().map(node -> new MetricComponentDTO(node.component().getKey(),
                        node.component().getName(), node.limits().cpuPercent(), node.limits().memoryBytes(),
                        node.counters())).toList(),
                view.samples().stream().map(sample -> new MetricSampleDTO(sample.getId(),
                        keys.get(sample.getExecutedComponentId()), sample.getSampledAt(), sample.getCpuPercent(),
                        sample.getMemoryBytes(), sample.getCounters())).toList());
    }
}
