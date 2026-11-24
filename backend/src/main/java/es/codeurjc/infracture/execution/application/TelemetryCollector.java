package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.MetricSample;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.execution.persistence.MetricSampleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Samples the CPU and memory of each node while the experiment runs (#93). It runs beside the reconciler, so
// neither waits for the other's Docker calls, and Docker is read outside any transaction. Metrics are
// evidence, not health: a failed read leaves a gap and the experiment goes on.
@Component
public class TelemetryCollector {

    private final ExecutionRepository executions;
    private final MetricSampleRepository samples;
    private final RuntimeEngine engine;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public TelemetryCollector(ExecutionRepository executions, MetricSampleRepository samples, RuntimeEngine engine,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.executions = executions;
        this.samples = samples;
        this.engine = engine;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedRateString = "${infracture.execution.metrics-interval:5s}")
    public void collect() {
        Target target = transactions.execute(status -> executions.findByReleasedAtIsNull()
                .filter(TelemetryCollector::isSampled).map(Target::of).orElse(null));
        if (target == null) {
            return;
        }
        Map<String, ResourceUsage> usage = read(target);
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> store(target, usage, now));
    }

    private Map<String, ResourceUsage> read(Target target) {
        try {
            return engine.usage(target.plan());
        } catch (RuntimeEngineException _) {
            return Map.of();
        }
    }

    // Checked again on the locked row: a Stop that arrived during the read ends the samples.
    private void store(Target target, Map<String, ResourceUsage> usage, Instant now) {
        boolean sampled = executions.findForUpdateById(target.id()).filter(TelemetryCollector::isSampled).isPresent();
        if (!sampled) {
            return;
        }
        List<MetricSample> rows = new ArrayList<>();
        target.services().forEach((componentId, service) -> {
            ResourceUsage reading = usage.getOrDefault(service, new ResourceUsage(null, null));
            rows.add(new MetricSample(componentId, now, reading.cpuPercent(), reading.memoryBytes()));
        });
        samples.saveAll(rows);
    }

    static boolean isSampled(Execution execution) {
        return execution.getPhase() == Phase.RUNNING && execution.getStopCause() == null;
    }

    // What the read needs after its transaction ends: the plan and each node's id with its service name.
    private record Target(Long id, Map<String, Object> plan, Map<Long, String> services) {

        static Target of(Execution execution) {
            Map<Long, String> services = new LinkedHashMap<>();
            for (ExecutedComponent component : execution.getComponents()) {
                services.put(component.getId(), PlanCompiler.serviceName(component));
            }
            return new Target(execution.getId(), execution.getPlan(), services);
        }
    }
}
