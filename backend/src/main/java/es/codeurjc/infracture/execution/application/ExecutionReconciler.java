package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// The control loop of section 9.5 in EXECUTION_ARCHITECTURE: it compares what the active execution
// asks for with what Docker should hold, and takes the next step. Each tick does at most one Docker
// step, outside any transaction. The phase changes around it are short transactions on the locked row.
@Component
public class ExecutionReconciler {

    private final ExecutionRepository repository;
    private final RuntimeEngine engine;
    private final PlanCompiler planCompiler;
    private final ExecutionObservations observations;
    private final ServiceProbe probe;
    private final Duration maxDuration;
    private final TransactionTemplate transactions;

    public ExecutionReconciler(ExecutionRepository repository, RuntimeEngine engine, PlanCompiler planCompiler,
            ExecutionObservations observations, ServiceProbe probe,
            @Value("${infracture.execution.max-duration:30m}") Duration maxDuration,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.engine = engine;
        this.planCompiler = planCompiler;
        this.observations = observations;
        this.probe = probe;
        this.maxDuration = maxDuration;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    // Spring runs scheduled methods on a single thread, so two ticks never overlap.
    @Scheduled(fixedDelayString = "${infracture.execution.reconcile-interval:2s}")
    public void reconcile() {
        Execution execution = transactions.execute(status -> nextStep());
        if (execution == null) {
            return;
        }
        switch (execution.getPhase()) {
            case STARTING -> start(execution);
            case RUNNING -> watch(execution);
            default -> stop(execution);
        }
    }

    // Records the next phase and returns the execution when that phase needs Docker.
    private Execution nextStep() {
        Optional<Execution> active = repository.findForUpdateByReleasedAtIsNull();
        if (active.isEmpty()) {
            return null;
        }
        Execution execution = active.get();
        if (execution.getStopCause() != null && execution.getPhase() != Phase.STOPPING) {
            execution.beginStopping();
        } else if (execution.getPhase() == Phase.RESERVED) {
            execution.startStarting();
        } else if (execution.getPhase() == Phase.STARTING) {
            // Only a backend restart leaves a start cut short. Stop cleans it up,
            // and #90 will record the interruption on its own.
            return null;
        }
        // The nodes are read after the transaction ends, so they are loaded now.
        execution.getComponents().size();
        return execution;
    }

    // A Stop pressed meanwhile is already recorded; the next tick starts the cleanup.
    private void start(Execution execution) {
        try {
            engine.up(execution.getPlan());
        } catch (RuntimeEngineException exception) {
            update(execution.getId(), locked -> locked.fail(exception.getMessage()));
            return;
        }
        update(execution.getId(), Execution::markRunning);
        for (Map.Entry<UUID, URI> target : planCompiler.evidence(execution).entrySet()) {
            observations.recordResponse(execution.getId(), target.getKey(), probe.get(target.getValue()));
        }
    }

    // Started is not the same as working: a node that falls over later fails the execution.
    private void watch(Execution execution) {
        Map<String, ComponentStatus> statuses;
        try {
            statuses = engine.status(execution.getPlan());
        } catch (RuntimeEngineException exception) {
            update(execution.getId(), locked -> locked.fail(exception.getMessage()));
            return;
        }
        String broken = recordStatuses(execution, statuses);
        if (broken != null) {
            update(execution.getId(), locked -> locked.fail(broken));
        } else if (!Instant.now().isBefore(execution.getCreatedAt().plus(maxDuration))) {
            // shortcut: the time limit ends like Stop until it gets a cause of its own.
            update(execution.getId(), Execution::requestStop);
        }
    }

    // Returns why the execution fails when a node is down, or null when all are up.
    private String recordStatuses(Execution execution, Map<String, ComponentStatus> statuses) {
        String broken = null;
        for (ExecutedComponent component : execution.getComponents()) {
            ComponentStatus status =
                    statuses.getOrDefault(PlanCompiler.serviceName(component), ComponentStatus.EXITED);
            observations.recordStatus(execution.getId(), component.getKey(), status);
            if (broken == null && status == ComponentStatus.UNHEALTHY) {
                broken = component.getName() + " is unhealthy";
            } else if (broken == null && status == ComponentStatus.EXITED) {
                broken = component.getName() + " stopped";
            }
        }
        return broken;
    }

    // A failed cleanup leaves the execution in STOPPING, so Run stays blocked and the next tick retries.
    private void stop(Execution execution) {
        try {
            engine.down(execution.getPlan(), planCompiler.labels(execution.getId()));
        } catch (RuntimeEngineException exception) {
            update(execution.getId(), locked -> locked.fail(exception.getMessage()));
            return;
        }
        update(execution.getId(), Execution::release);
        observations.forget(execution.getId());
    }

    private void update(Long id, Consumer<Execution> change) {
        transactions.executeWithoutResult(status -> change.accept(repository.findForUpdateById(id).orElseThrow()));
    }
}
