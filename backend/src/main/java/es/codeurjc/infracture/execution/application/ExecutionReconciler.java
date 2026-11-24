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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final ExecutionJournal journal;
    private final ServiceProbe probe;
    private final Duration maxDuration;
    private final Duration cleanupPause;
    private final Clock clock;
    private final TransactionTemplate transactions;
    // Only one backend runs, so an open execution created before this process started was left by an
    // earlier one (#90). Each one is interrupted once per process; the set only holds those leftovers.
    private final Instant processStartedAt;
    private final Set<Long> recovered = new HashSet<>();
    // Until when the logs of each running execution were read. Only the ticks use it, one at a time.
    private final Map<Long, Instant> logsReadUntil = new HashMap<>();

    public ExecutionReconciler(ExecutionRepository repository, RuntimeEngine engine, PlanCompiler planCompiler,
            ExecutionJournal journal, ServiceProbe probe,
            @Value("${infracture.execution.max-duration:30m}") Duration maxDuration,
            @Value("${infracture.execution.cleanup-retry-pause:10s}") Duration cleanupPause,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.engine = engine;
        this.planCompiler = planCompiler;
        this.journal = journal;
        this.probe = probe;
        this.maxDuration = maxDuration;
        this.cleanupPause = cleanupPause;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.processStartedAt = clock.instant();
    }

    // Spring never runs a scheduled method twice at once, so two ticks never overlap.
    @Scheduled(fixedDelayString = "${infracture.execution.reconcile-interval:2s}")
    public void reconcile() {
        Execution execution = transactions.execute(status -> nextStep());
        if (execution == null) {
            return;
        }
        // Marked once the interruption is committed, so a failed transaction finds the leftover again.
        if (isLeftover(execution)) {
            recovered.add(execution.getId());
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
        ExecutionJournal.Mark before = ExecutionJournal.mark(execution);
        boolean needsDocker = advance(execution, now);
        journal.recordChanges(before, execution, now);
        // The steps below run after this transaction ends, so the frozen load is read now.
        execution.getWorkload();
        return needsDocker ? execution : null;
    }

    // Takes the next phase on the locked row and tells whether that phase needs Docker now.
    private boolean advance(Execution execution, Instant now) {
        // Checked here, on the locked row, so a leftover reservation never starts and no request races it.
        if (isLeftover(execution)) {
            execution.interrupt(now);
        }
        // Checked before any Docker call, so the limit holds even when Docker cannot answer.
        if (execution.isPastDeadline(now)) {
            execution.reachTimeLimit();
        }
        if (execution.getStopCause() != null && execution.getPhase() != Phase.STOPPING) {
            execution.beginStopping();
            return true;
        }
        if (execution.getPhase() == Phase.RESERVED) {
            execution.startStarting();
            return true;
        }
        // A start of this process cut short by a database error waits for Stop. One left by an earlier
        // process was interrupted above.
        if (execution.getPhase() == Phase.STARTING) {
            return false;
        }
        // A failed cleanup waits for its pause, or for the owner once the automatic attempts are spent.
        return execution.getPhase() != Phase.STOPPING || execution.isCleanupDue(now, cleanupPause);
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
            String response = probe.get(target.getValue());
            if (response != null) {
                transactions.executeWithoutResult(status -> journal.recordResponse(execution.getId(), target.getKey(),
                        response, clock.instant()));
            }
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
        readLogs(execution);
    }

    // Returns why the execution fails when a node is down, or null when all are up.
    private String recordStatuses(Execution execution, Map<String, ComponentStatus> statuses) {
        String broken = null;
        Map<UUID, ComponentStatus> byNode = new LinkedHashMap<>();
        for (ExecutedComponent component : execution.getComponents()) {
            ComponentStatus status =
                    statuses.getOrDefault(PlanCompiler.serviceName(component), ComponentStatus.EXITED);
            byNode.put(component.getKey(), status);
            if (broken == null && status == ComponentStatus.UNHEALTHY) {
                broken = component.getName() + " is unhealthy";
            } else if (broken == null && status == ComponentStatus.EXITED) {
                broken = component.getName() + " stopped";
            }
        }
        transactions.executeWithoutResult(status -> journal.recordStates(execution.getId(), byNode, clock.instant()));
        return broken;
    }

    // Logs are evidence, not health: when Docker cannot give them, the experiment goes on without them.
    private void readLogs(Execution execution) {
        Long id = execution.getId();
        Instant since = logsReadUntil.containsKey(id) ? logsReadUntil.get(id) : journal.lastLogAt(id);
        List<LogLine> lines;
        try {
            lines = engine.logs(execution.getPlan(), since);
        } catch (RuntimeEngineException _) {
            return;
        }
        Instant readUntil = transactions.execute(status -> journal.recordLogs(execution, lines, since));
        if (readUntil != null) {
            logsReadUntil.put(id, readUntil);
        }
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
        logsReadUntil.remove(execution.getId());
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

    private boolean isLeftover(Execution execution) {
        return execution.getCreatedAt().isBefore(processStartedAt) && !recovered.contains(execution.getId());
    }

    // Each change is recorded as events in its own transaction.
    private void update(Long id, Consumer<Execution> change) {
        transactions.executeWithoutResult(status -> {
            Execution locked = repository.findForUpdateById(id).orElseThrow();
            ExecutionJournal.Mark before = ExecutionJournal.mark(locked);
            change.accept(locked);
            journal.recordChanges(before, locked, clock.instant());
        });
    }
}
