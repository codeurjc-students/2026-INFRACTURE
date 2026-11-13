package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.ExecutedWorkload;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
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
    private final Duration cleanupPause;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ExecutionReconciler(ExecutionRepository repository, RuntimeEngine engine, PlanCompiler planCompiler,
            ExecutionObservations observations, ServiceProbe probe,
            @Value("${infracture.execution.max-duration:30m}") Duration maxDuration,
            @Value("${infracture.execution.cleanup-retry-pause:10s}") Duration cleanupPause,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.engine = engine;
        this.planCompiler = planCompiler;
        this.observations = observations;
        this.probe = probe;
        this.maxDuration = maxDuration;
        this.cleanupPause = cleanupPause;
        this.clock = clock;
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
        Instant now = clock.instant();
        // Checked before any Docker call, so the limit holds even when Docker cannot answer.
        if (execution.isPastDeadline(now)) {
            execution.reachTimeLimit();
        }
        if (execution.getStopCause() != null && execution.getPhase() != Phase.STOPPING) {
            execution.beginStopping();
        } else if (execution.getPhase() == Phase.RESERVED) {
            execution.startStarting();
        } else if (execution.getPhase() == Phase.STARTING) {
            // Only a backend restart leaves a start cut short. Stop cleans it up,
            // and #90 will record the interruption on its own.
            return null;
        } else if (execution.getPhase() == Phase.STOPPING && !execution.isCleanupDue(now, cleanupPause)) {
            // A failed cleanup waits for its pause, or for the owner once the automatic attempts are spent.
            return null;
        }
        // The steps below run after this transaction ends, so the frozen load is read now.
        execution.getWorkload();
        return execution;
    }

    // A Stop pressed meanwhile cancels the start; the next tick cleans up what it left.
    // The load starts only once every node is healthy, so its results can be interpreted.
    private void start(Execution execution) {
        BooleanSupplier stopping = () -> isStopping(execution.getId());
        try {
            engine.up(execution.getPlan(), stopping);
            if (execution.getWorkload() != null) {
                engine.startLoad(execution.getPlan(), stopping);
            }
        } catch (StartCancelledException _) {
            // The cause is already recorded, and a cancelled start is not a failure.
            return;
        } catch (RuntimeEngineException exception) {
            update(execution.getId(), locked -> locked.fail(exception.getMessage()));
            return;
        }
        update(execution.getId(), locked -> locked.markRunning(clock.instant(), maxDuration));
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
        String nodeDown = recordStatuses(execution, statuses);
        String broken = nodeDown == null && statuses.get(PlanCompiler.LOAD_SERVICE) == ComponentStatus.EXITED
                ? collectEndedLoad(execution)
                : nodeDown;
        if (broken != null) {
            update(execution.getId(), locked -> locked.fail(broken));
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

    // k6 exits on its own when its duration ends; the experiment keeps running until Stop or the time limit.
    // Returns why the execution fails when k6 ended any other way, or null.
    private String collectEndedLoad(Execution execution) {
        ExecutedWorkload load = execution.getWorkload();
        if (load == null || load.hasSummary()) {
            return null;
        }
        LoadReport report;
        try {
            report = engine.stopLoad(execution.getPlan());
        } catch (RuntimeEngineException exception) {
            return exception.getMessage();
        }
        recordLoad(execution, report);
        return report.completed() ? null : "The load stopped with exit code " + report.exitCode();
    }

    // A failed cleanup leaves the execution in STOPPING, so Run stays blocked until a later attempt
    // succeeds. It is recorded apart from the cause, so it does not change how the execution ended.
    // The load stops first, so it generates no more work, and its summary is kept before the rest goes.
    private void stop(Execution execution) {
        ExecutedWorkload load = execution.getWorkload();
        if (load != null && !load.hasSummary()) {
            try {
                recordLoad(execution, engine.stopLoad(execution.getPlan()));
            } catch (RuntimeEngineException _) {
                // The cleanup goes on; the summary stays missing rather than invented.
            }
        }
        try {
            engine.down(execution.getPlan(), planCompiler.labels(execution.getId()));
        } catch (RuntimeEngineException exception) {
            update(execution.getId(), locked -> locked.recordCleanupFailure(exception.getMessage(), clock.instant()));
            return;
        }
        update(execution.getId(), Execution::release);
        observations.forget(execution.getId());
    }

    // A load that never started left no container and nothing to record.
    private void recordLoad(Execution execution, LoadReport report) {
        if (report.exitCode() != null) {
            update(execution.getId(), locked -> locked.recordLoadSummary(report.summary(), report.completed()));
        }
    }

    // Asked every second while a start waits. If the database cannot answer, the start goes on and the
    // next answer decides.
    private boolean isStopping(Long id) {
        try {
            return repository.existsByIdAndStopCauseIsNotNull(id);
        } catch (DataAccessException _) {
            return false;
        }
    }

    private void update(Long id, Consumer<Execution> change) {
        transactions.executeWithoutResult(status -> change.accept(repository.findForUpdateById(id).orElseThrow()));
    }
}
