package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.application.ExecutionView.Node;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import es.codeurjc.infracture.execution.domain.ExecutionEvent.Kind;
import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import es.codeurjc.infracture.execution.domain.LoadSummary;
import es.codeurjc.infracture.execution.domain.Outcome;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.domain.StopCause;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.Workload;
import es.codeurjc.infracture.security.MutableClock;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class ExecutionReconcilerTests {

    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final String UP = "up infracture-local-exec-12";
    private static final String DOWN = "down infracture-local-exec-12 "
            + "{io.infracture.instance=local, io.infracture.execution=12}";
    private static final RuntimeStatus HEALTHY = new RuntimeStatus(Map.of(ORDERS, ComponentStatus.HEALTHY), null);
    private static final RuntimeStatus UNHEALTHY =
            new RuntimeStatus(Map.of(ORDERS, ComponentStatus.UNHEALTHY), null);
    private static final RuntimeStatus LOAD_ENDED =
            new RuntimeStatus(Map.of(ORDERS, ComponentStatus.HEALTHY), ComponentStatus.EXITED);
    private static final String START_LOAD = "start-load";
    private static final String STOP_LOAD = "stop-load";
    private static final Workload LOAD = new Workload(ORDERS, "CONSTANT_HTTP",
            Map.of("requestsPerSecond", 10, "virtualUsers", 5, "durationSeconds", 60), 42);
    private static final LoadSummary SUMMARY = new LoadSummary(100L, 0L, 0L, 52.5, 61.0);

    private final ExecutionRepository repository = mock(ExecutionRepository.class);
    private final FakeEngine engine = new FakeEngine();
    private final PlanCompiler planCompiler =
            new PlanCompiler(List.of(new HttpServiceDriver("infracture/demo-app:0.1.0")), "local",
                    "infracture/load-generator:0.1.0");
    private final InMemoryExecutionEvents events = new InMemoryExecutionEvents();
    private final ExecutionJournal journal =
            new ExecutionJournal(events.repository, new LogRedactor("db-password-2026", "admin-password-2026"));
    private final ServiceProbe probe = mock(ServiceProbe.class);
    private static final Instant STARTED = Instant.parse("2026-11-13T10:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-11-13T10:30:00Z");
    private static final Instant RESTARTED = Instant.parse("2026-11-13T10:05:00Z");
    // One reconciler is one backend process; a restart builds a new one later on the same clock.
    private final MutableClock clock = new MutableClock(STARTED);
    private final ExecutionReconciler reconciler = reconciler(Duration.ZERO, clock);
    private Execution execution;

    @BeforeEach
    void reserveWithoutLoad() {
        reserve(null);
    }

    private void reserve(Workload load) {
        execution = new Execution(statelessScenario(load), Map.of(ComponentType.HTTP_SERVICE, 1));
        ReflectionTestUtils.setField(execution, "id", 12L);
        // Created by the process under test unless a test restarts the backend.
        ReflectionTestUtils.setField(execution, "createdAt", STARTED);
        execution.attachPlan(planCompiler.compile(execution));
        when(repository.findForUpdateByReleasedAtIsNull())
                .thenAnswer(invocation -> execution.isActive() ? Optional.of(execution) : Optional.empty());
        when(repository.findForUpdateById(12L)).thenReturn(Optional.of(execution));
        when(repository.existsByIdAndStopCauseIsNotNull(12L))
                .thenAnswer(invocation -> execution.getStopCause() != null);
    }

    @Test
    void startsTheReservedExecutionAndKeepsItRunningUntilStop() {
        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.RUNNING);
        assertThat(engine.calls).containsExactly(UP);
    }

    @Test
    void cleansUpAndReleasesOnStop() {
        reconciler.reconcile();
        execution.requestStop();

        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.RELEASED);
        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(engine.calls).containsExactly(UP, DOWN);
        reconciler.reconcile();
        assertThat(engine.calls).hasSize(2);
    }

    @Test
    void stopsAReservationWithoutStartingIt() {
        execution.requestStop();

        reconciler.reconcile();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(engine.calls).containsExactly(DOWN);
    }

    @Test
    void recordsAFailedStartAndCleansUpWhatItCreated() {
        engine.upFailure = new RuntimeEngineException("docker compose up failed: container is unhealthy");

        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.STARTING);
        assertThat(execution.getStopCause()).isEqualTo(StopCause.FAILURE);
        reconciler.reconcile();
        assertThat(execution.getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo("docker compose up failed: container is unhealthy");
        assertThat(engine.calls).containsExactly(UP, DOWN);
    }

    @Test
    void keepsTheReservationAndRetriesWhenCleanupFailsWithoutChangingTheOutcome() {
        reconciler.reconcile();
        execution.requestStop();
        engine.downFailure = new RuntimeEngineException("Cleanup left 1 Docker resources with the execution labels");

        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.STOPPING);
        assertThat(execution.isActive()).isTrue();
        assertThat(execution.getCleanupFailures()).isEqualTo(1);
        assertThat(execution.getCleanupError())
                .isEqualTo("Cleanup left 1 Docker resources with the execution labels");
        assertThat(execution.getStopCause()).isEqualTo(StopCause.STOP_REQUESTED);
        engine.downFailure = null;
        reconciler.reconcile();
        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(execution.getFailureReason()).isNull();
        assertThat(engine.calls).containsExactly(UP, DOWN, DOWN);
    }

    @Test
    void waitsBeforeRetryingAFailedCleanup() {
        ExecutionReconciler patient = reconciler(Duration.ofHours(1), clock);
        patient.reconcile();
        execution.requestStop();
        engine.downFailure = new RuntimeEngineException("docker network rm failed: network has active endpoints");

        patient.reconcile();
        patient.reconcile();

        assertThat(engine.calls).containsExactly(UP, DOWN);
    }

    @Test
    void stopsRetryingAfterTheAutomaticAttemptsUntilStopIsPressedAgain() {
        reconciler.reconcile();
        execution.requestStop();
        engine.downFailure = new RuntimeEngineException("docker volume rm failed: volume is in use");

        for (int tick = 0; tick < 5; tick++) {
            reconciler.reconcile();
        }

        assertThat(engine.calls).containsExactly(UP, DOWN, DOWN, DOWN);
        assertThat(execution.isCleanupRetryNeeded()).isTrue();
        assertThat(execution.isActive()).isTrue();
        engine.downFailure = null;
        execution.requestStop();
        reconciler.reconcile();
        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(engine.calls).containsExactly(UP, DOWN, DOWN, DOWN, DOWN);
    }

    @Test
    void cancelsTheStartWhenStopArrivesWhileTheNodesStart() {
        engine.duringUp = execution::requestStop;

        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.STARTING);
        assertThat(execution.getStopCause()).isEqualTo(StopCause.STOP_REQUESTED);
        reconciler.reconcile();
        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(execution.getFailureReason()).isNull();
        assertThat(engine.calls).containsExactly(UP, DOWN);
    }

    @Test
    void cancelsTheLoadStartAndCleansUpWithoutRunning() {
        reserve(LOAD);
        engine.duringStartLoad = execution::requestStop;
        engine.loadReport = new LoadReport(null, null);

        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.STARTING);
        reconciler.reconcile();
        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN);
    }

    @Test
    void failsWhenTheStartFailsAfterStopArrivedMeanwhile() {
        engine.duringUp = execution::requestStop;
        engine.upFailure = new RuntimeEngineException("docker compose up failed: pull access denied");

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo("docker compose up failed: pull access denied");
    }

    // Only a database error after the start leaves this process's own execution in STARTING.
    @Test
    void leavesAStartThisProcessCutShortUntilStop() {
        execution.startStarting();

        reconciler.reconcile();
        assertThat(engine.calls).isEmpty();

        execution.requestStop();
        reconciler.reconcile();
        assertThat(execution.getPhase()).isEqualTo(Phase.RELEASED);
        assertThat(engine.calls).containsExactly(DOWN);
    }

    @Test
    void neverStartsAReservationLeftByAnEarlierProcess() {
        ExecutionReconciler restarted = restartAt(RESTARTED);

        restarted.reconcile();

        assertThat(engine.calls).containsExactly(DOWN);
        assertThat(execution.getOutcome()).isEqualTo(Outcome.INTERRUPTED);
        assertThat(execution.getInterruptedAt()).isEqualTo(RESTARTED);
        assertThat(execution.getInterruptedPhase()).isEqualTo(Phase.RESERVED);
    }

    @Test
    void interruptsAStartCutShortByARestartAndCleansItUp() {
        execution.startStarting();

        restartAt(RESTARTED).reconcile();

        assertThat(engine.calls).containsExactly(DOWN);
        assertThat(execution.getOutcome()).isEqualTo(Outcome.INTERRUPTED);
        assertThat(execution.getInterruptedPhase()).isEqualTo(Phase.STARTING);
    }

    @Test
    void stopsTheLoadAndKeepsItsSummaryWhenARestartFindsTheExperimentRunning() {
        reserve(LOAD);
        reconciler.reconcile();

        restartAt(RESTARTED).reconcile();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.INTERRUPTED);
        assertThat(execution.getInterruptedPhase()).isEqualTo(Phase.RUNNING);
        assertThat(execution.getWorkload().getSummary()).isEqualTo(SUMMARY);
        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN);
    }

    // Each restart gives the cleanup new automatic attempts, but repeats nothing already done.
    @Test
    void keepsRunBlockedWhileDockerCannotCleanUpAfterARestartAndDuplicatesNothingOnTheNext() {
        reserve(LOAD);
        reconciler.reconcile();
        engine.downFailure = new RuntimeEngineException("docker compose down failed: Cannot connect");

        ExecutionReconciler first = restartAt(RESTARTED);
        for (int tick = 0; tick < 5; tick++) {
            first.reconcile();
        }

        assertThat(execution.getPhase()).isEqualTo(Phase.STOPPING);
        assertThat(execution.getStopCause()).isEqualTo(StopCause.INTERRUPTION);
        assertThat(execution.isCleanupRetryNeeded()).isTrue();
        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN, DOWN, DOWN);
        engine.downFailure = null;
        restartAt(RESTARTED.plusSeconds(60)).reconcile();
        assertThat(execution.getOutcome()).isEqualTo(Outcome.INTERRUPTED);
        assertThat(execution.getInterruptedAt()).isEqualTo(RESTARTED);
        assertThat(execution.getInterruptedPhase()).isEqualTo(Phase.RUNNING);
        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN, DOWN, DOWN, DOWN);
    }

    @Test
    void keepsTheRealResponseAndTheStatusOfEachNode() {
        when(probe.get(URI.create("http://c-" + ORDERS + ":8080/"))).thenReturn("{\"component\":\"orders-api\"}");

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(journal.nodes(12L)).containsEntry(ORDERS,
                new Node(ComponentStatus.HEALTHY, "{\"component\":\"orders-api\"}"));
    }

    @Test
    void failsWhenANodeFallsOverWhileRunning() {
        reconciler.reconcile();
        engine.status = UNHEALTHY;

        reconciler.reconcile();

        assertThat(execution.getStopCause()).isEqualTo(StopCause.FAILURE);
        assertThat(execution.getFailureReason()).isEqualTo("orders-api is unhealthy");
        assertThat(journal.nodes(12L).get(ORDERS).status()).isEqualTo(ComponentStatus.UNHEALTHY);
    }

    @Test
    void failsWhenANodeDisappears() {
        reconciler.reconcile();
        engine.status = new RuntimeStatus(Map.of(), null);

        reconciler.reconcile();

        assertThat(execution.getFailureReason()).isEqualTo("orders-api stopped");
    }

    @Test
    void failsWhenDockerCannotReportTheNodes() {
        reconciler.reconcile();
        engine.statusFailure = new RuntimeEngineException("docker compose ps failed: Cannot connect");

        reconciler.reconcile();

        assertThat(execution.getFailureReason()).isEqualTo("docker compose ps failed: Cannot connect");
    }

    @Test
    void stopsTheLoadAndCleansUpAtTheTimeLimitCountedFromRunning() {
        reserve(LOAD);
        at(STARTED).reconcile();
        at(DEADLINE.minusSeconds(1)).reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.RUNNING);
        assertThat(execution.getDeadlineAt()).isEqualTo(DEADLINE);
        at(DEADLINE).reconcile();
        assertThat(execution.getStopCause()).isEqualTo(StopCause.TIME_LIMIT);
        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(execution.getWorkload().getSummary()).isEqualTo(SUMMARY);
        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN);
    }

    @Test
    void appliesTheTimeLimitEvenWhenDockerCannotReportTheNodes() {
        at(STARTED).reconcile();
        engine.statusFailure = new RuntimeEngineException("docker compose ps failed: Cannot connect");

        at(DEADLINE).reconcile();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(execution.getStopCause()).isEqualTo(StopCause.TIME_LIMIT);
        assertThat(execution.getFailureReason()).isNull();
    }

    @Test
    void keepsTheReservationWhenTheCleanupAtTheTimeLimitFails() {
        at(STARTED).reconcile();
        engine.downFailure = new RuntimeEngineException("docker network rm failed: network has active endpoints");

        at(DEADLINE).reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.STOPPING);
        assertThat(execution.getStopCause()).isEqualTo(StopCause.TIME_LIMIT);
        assertThat(execution.getCleanupFailures()).isEqualTo(1);
        assertThat(execution.getReleasedAt()).isNull();
    }

    @Test
    void recordsEachStepOfAFailedStartAsAnEvent() {
        engine.upFailure = new RuntimeEngineException("docker compose up failed: container is unhealthy");

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(events.types(12L)).containsExactly("PHASE", "STOP", "PHASE", "PHASE");
        assertThat(events.of(12L, Kind.EVENT)).extracting(ExecutionEvent::getPayload).containsExactly(
                Map.of("type", "PHASE", "phase", "STARTING"),
                Map.of("type", "STOP", "cause", "FAILURE",
                        "reason", "docker compose up failed: container is unhealthy"),
                Map.of("type", "PHASE", "phase", "STOPPING"),
                Map.of("type", "PHASE", "phase", "RELEASED"));
    }

    @Test
    void recordsTheStateOfANodeOnlyWhenItChanges() {
        reconciler.reconcile();
        reconciler.reconcile();
        reconciler.reconcile();
        engine.status = UNHEALTHY;
        reconciler.reconcile();

        assertThat(events.of(12L, Kind.STATE)).extracting(event -> event.getPayload().get("status"))
                .containsExactly("HEALTHY", "UNHEALTHY");
    }

    @Test
    void keepsTheLinesTheNodesAndTheLoadPrintOnceAndCleaned() {
        Instant printed = STARTED.plusSeconds(1);
        engine.logLines.add(new LogLine(ORDERS, printed, "Started DemoApplication"));
        engine.logLines.add(new LogLine(ORDERS, printed.plusMillis(1), "connecting with password=hunter2"));
        engine.logLines.add(new LogLine(null, printed.plusMillis(2), "running (01m00s)"));

        reconciler.reconcile();
        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(events.of(12L, Kind.LOG)).extracting(ExecutionEvent::getComponentKey,
                event -> event.getPayload().get("line"), ExecutionEvent::getAt).containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ORDERS, "Started DemoApplication", printed),
                        org.assertj.core.groups.Tuple.tuple(ORDERS, "connecting with password=[redacted]",
                                printed.plusMillis(1)),
                        org.assertj.core.groups.Tuple.tuple(null, "running (01m00s)", printed.plusMillis(2)));
    }

    @Test
    void goesOnWhenDockerCannotGiveTheLogs() {
        reconciler.reconcile();
        engine.logsFailure = new RuntimeEngineException("docker compose logs failed: Cannot connect");

        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.RUNNING);
        assertThat(execution.getStopCause()).isNull();
    }

    @Test
    void startsTheLoadOnlyOnceTheNodesAreHealthy() {
        reserve(LOAD);

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.RUNNING);
        assertThat(engine.calls).containsExactly(UP, START_LOAD);
    }

    @Test
    void stopsTheLoadAndKeepsItsSummaryBeforeRemovingTheRest() {
        reserve(LOAD);
        reconciler.reconcile();
        execution.requestStop();

        reconciler.reconcile();

        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN);
        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(execution.getWorkload().getCompleted()).isFalse();
        assertThat(execution.getWorkload().getSummary()).isEqualTo(SUMMARY);
        assertThat(execution.getWorkload().getSummaryCollectedAt()).isNotNull();
    }

    @Test
    void keepsTheExperimentRunningWhenTheLoadEndsOnItsOwn() {
        reserve(LOAD);
        reconciler.reconcile();
        engine.status = LOAD_ENDED;
        engine.loadReport = new LoadReport(0, SUMMARY);

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.RUNNING);
        assertThat(execution.getStopCause()).isNull();
        assertThat(execution.getWorkload().getCompleted()).isTrue();
        execution.requestStop();
        reconciler.reconcile();
        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN);
    }

    @Test
    void failsWhenTheLoadEndsWithAnErrorAndKeepsWhatItReported() {
        reserve(LOAD);
        reconciler.reconcile();
        engine.status = LOAD_ENDED;
        engine.loadReport = new LoadReport(107, null);

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo("The load stopped with exit code 107");
        assertThat(execution.getWorkload().getCompleted()).isFalse();
        assertThat(execution.getWorkload().getSummary()).isNull();
        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN);
    }

    @Test
    void failsAndCleansUpWhenTheLoadCannotStart() {
        reserve(LOAD);
        engine.startLoadFailure = new RuntimeEngineException("docker compose up failed: no such image");
        engine.loadReport = new LoadReport(null, null);

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo("docker compose up failed: no such image");
        assertThat(execution.getWorkload().hasSummary()).isFalse();
        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN);
    }

    @Test
    void cleansUpWithoutASummaryWhenTheLoadCannotBeStopped() {
        reserve(LOAD);
        reconciler.reconcile();
        execution.requestStop();
        engine.stopLoadFailure = new RuntimeEngineException("docker compose stop failed: Cannot connect");

        reconciler.reconcile();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(execution.getWorkload().hasSummary()).isFalse();
        assertThat(engine.calls).containsExactly(UP, START_LOAD, STOP_LOAD, DOWN);
    }

    @Test
    void doesNothingWithoutAnActiveExecution() {
        when(repository.findForUpdateByReleasedAtIsNull()).thenReturn(Optional.empty());

        reconciler.reconcile();

        assertThat(engine.calls).isEmpty();
    }

    private ExecutionReconciler at(Instant now) {
        clock.advance(Duration.between(clock.instant(), now));
        return reconciler;
    }

    // A new backend process starts at that instant and finds what the earlier one left.
    private ExecutionReconciler restartAt(Instant now) {
        clock.advance(Duration.between(clock.instant(), now));
        return reconciler(Duration.ZERO, clock);
    }

    private ExecutionReconciler reconciler(Duration cleanupPause, Clock clock) {
        return new ExecutionReconciler(repository, engine, journal, probe, Duration.ofMinutes(30),
                cleanupPause, clock, mock(PlatformTransactionManager.class));
    }

    private static Scenario statelessScenario(Workload load) {
        ComponentTemplate http = new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE,
                true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)));
        Scenario scenario = new Scenario(new Project(
                new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER), "Lab", null), "Run", null);
        scenario.replaceGraph(1, List.of(new ComponentDraft(ORDERS, "http-service", "orders-api", 0, 0,
                new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)))), List.of(), load,
                Map.of("http-service", http));
        return scenario;
    }

    private static final class FakeEngine implements RuntimeEngine {

        private final List<String> calls = new ArrayList<>();
        private RuntimeEngineException upFailure;
        private RuntimeEngineException downFailure;
        private RuntimeEngineException statusFailure;
        private RuntimeEngineException startLoadFailure;
        private RuntimeEngineException stopLoadFailure;
        private RuntimeEngineException logsFailure;
        private final List<LogLine> logLines = new ArrayList<>();
        // What happens while the engine waits, such as a Stop from the owner.
        private Runnable duringUp = () -> {};
        private Runnable duringStartLoad = () -> {};
        private RuntimeStatus status = HEALTHY;
        // k6 exits with 105 when Stop interrupts it.
        private LoadReport loadReport = new LoadReport(105, SUMMARY);

        @Override
        public void up(ExecutionPlan plan, BooleanSupplier cancelled) {
            calls.add("up " + plan.project());
            duringUp.run();
            if (upFailure != null) {
                throw upFailure;
            }
            cancelIf(cancelled);
        }

        @Override
        public void startLoad(ExecutionPlan plan, BooleanSupplier cancelled) {
            calls.add("start-load");
            duringStartLoad.run();
            if (startLoadFailure != null) {
                throw startLoadFailure;
            }
            cancelIf(cancelled);
        }

        private static void cancelIf(BooleanSupplier cancelled) {
            if (cancelled.getAsBoolean()) {
                throw new StartCancelledException("docker compose up was cancelled because the execution is stopping");
            }
        }

        @Override
        public LoadReport stopLoad(ExecutionPlan plan) {
            calls.add("stop-load");
            if (stopLoadFailure != null) {
                throw stopLoadFailure;
            }
            return loadReport;
        }

        @Override
        public RuntimeStatus status(ExecutionPlan plan) {
            if (statusFailure != null) {
                throw statusFailure;
            }
            return status;
        }

        // Like Docker, a line printed exactly at since comes back again.
        @Override
        public List<LogLine> logs(ExecutionPlan plan, Instant since) {
            if (logsFailure != null) {
                throw logsFailure;
            }
            return logLines.stream().filter(line -> since == null || !line.at().isBefore(since)).toList();
        }

        @Override
        public Map<UUID, ResourceUsage> usage(ExecutionPlan plan) {
            return Map.of();
        }

        @Override
        public void down(ExecutionPlan plan) {
            calls.add("down " + plan.project() + " " + plan.labels());
            if (downFailure != null) {
                throw downFailure;
            }
        }
    }
}
