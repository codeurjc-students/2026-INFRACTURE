package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.MetricSample;
import es.codeurjc.infracture.execution.persistence.MetricSampleRepository;
import es.codeurjc.infracture.shared.InvalidDataException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Reads the CPU and memory series of every node of an execution, for its owner, while it runs and after it
// ends. One read covers all the nodes, so watching many of them costs one request.
@Service
public class ExecutionMetricsService {

    public static final int MAX_SAMPLES = 1000;
    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T23:59:59Z");

    private final ExecutionService executions;
    private final MetricSampleRepository samples;
    private final Duration interval;

    public ExecutionMetricsService(ExecutionService executions, MetricSampleRepository samples,
            @Value("${infracture.execution.metrics-interval:5s}") Duration interval) {
        this.executions = executions;
        this.samples = samples;
        this.interval = interval;
    }

    // A node as it was frozen for the execution, with the limits it ran with.
    public record NodeLimits(ExecutedComponent component, ResourceUsage limits) {
    }

    // capturing: more samples may still come. nextAfter: the cursor of the next read.
    public record MetricsView(List<NodeLimits> nodes, long intervalSeconds, boolean capturing,
            List<MetricSample> samples, boolean hasMore, long nextAfter) {
    }

    @Transactional(readOnly = true)
    public MetricsView read(String ownerEmail, Long projectId, Long scenarioId, Long executionId, Instant from,
            Instant to, long after, int limit) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDataException("from must not be after to");
        }
        if (after < 0) {
            throw new InvalidDataException("after must not be negative");
        }
        if (limit < 1 || limit > MAX_SAMPLES) {
            throw new InvalidDataException("limit must be between 1 and %d".formatted(MAX_SAMPLES));
        }
        Execution execution = executions.ownExecution(ownerEmail, projectId, scenarioId, executionId, false);
        List<NodeLimits> nodes = execution.getComponents().stream()
                .map(component -> new NodeLimits(component, PlanCompiler.limits(execution.getPlan(), component)))
                .toList();
        List<Long> ids = execution.getComponents().stream().map(ExecutedComponent::getId).toList();

        // One more than asked for tells whether another page follows.
        List<MetricSample> found = samples.findByExecutedComponentIdInAndIdGreaterThanAndSampledAtBetweenOrderById(
                ids, after, from != null ? from : EARLIEST, to != null ? to : LATEST, Limit.of(limit + 1));
        boolean hasMore = found.size() > limit;
        List<MetricSample> page = hasMore ? found.subList(0, limit) : found;
        long nextAfter = page.isEmpty() ? after : page.getLast().getId();
        return new MetricsView(nodes, interval.toSeconds(), TelemetryCollector.isSampled(execution),
                List.copyOf(page), hasMore, nextAfter);
    }
}
