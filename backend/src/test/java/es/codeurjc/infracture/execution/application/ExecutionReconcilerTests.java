package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.application.ExecutionObservations.Observation;
import es.codeurjc.infracture.execution.domain.Execution;
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
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class ExecutionReconcilerTests {

    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final String UP = "up infracture-local-exec-12";
    private static final String DOWN = "down infracture-local-exec-12 "
            + "{io.infracture.instance=local, io.infracture.execution=12}";
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
    private final ExecutionObservations observations = new ExecutionObservations();
    private final ServiceProbe probe = mock(ServiceProbe.class);
    private final ExecutionReconciler reconciler = reconciler(Duration.ofMinutes(30));
    private Execution execution;

    @BeforeEach
    void reserveWithoutLoad() {
        reserve(null);
    }

    private void reserve(Workload load) {
        execution = new Execution(statelessScenario(load), Map.of(ComponentType.HTTP_SERVICE, 1));
        ReflectionTestUtils.setField(execution, "id", 12L);
        ReflectionTestUtils.setField(execution, "createdAt", Instant.now());
        execution.attachPlan(planCompiler.compile(execution));
        when(repository.findForUpdateByReleasedAtIsNull())
                .thenAnswer(invocation -> execution.isActive() ? Optional.of(execution) : Optional.empty());
        when(repository.findForUpdateById(12L)).thenReturn(Optional.of(execution));
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
    void keepsTheReservationAndRetriesWhenCleanupFails() {
        reconciler.reconcile();
        execution.requestStop();
        engine.downFailure = new RuntimeEngineException("Cleanup left 1 Docker resources with the execution labels");

        reconciler.reconcile();

        assertThat(execution.getPhase()).isEqualTo(Phase.STOPPING);
        assertThat(execution.isActive()).isTrue();
        engine.downFailure = null;
        reconciler.reconcile();
        assertThat(execution.getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(engine.calls).containsExactly(UP, DOWN, DOWN);
    }

    @Test
    void leavesAStartCutShortByARestartUntilStop() {
        execution.startStarting();

        reconciler.reconcile();
        assertThat(engine.calls).isEmpty();

        execution.requestStop();
        reconciler.reconcile();
        assertThat(execution.getPhase()).isEqualTo(Phase.RELEASED);
        assertThat(engine.calls).containsExactly(DOWN);
    }

    @Test
    void keepsTheRealResponseAndTheStatusOfEachNode() {
        when(probe.get(URI.create("http://c-" + ORDERS + ":8080/"))).thenReturn("{\"component\":\"orders-api\"}");

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(observations.of(12L, ORDERS))
                .isEqualTo(new Observation(ComponentStatus.HEALTHY, "{\"component\":\"orders-api\"}"));
    }

    @Test
    void failsWhenANodeFallsOverWhileRunning() {
        reconciler.reconcile();
        engine.status = Map.of("c-" + ORDERS, ComponentStatus.UNHEALTHY);

        reconciler.reconcile();

        assertThat(execution.getStopCause()).isEqualTo(StopCause.FAILURE);
        assertThat(execution.getFailureReason()).isEqualTo("orders-api is unhealthy");
        assertThat(observations.of(12L, ORDERS).status()).isEqualTo(ComponentStatus.UNHEALTHY);
    }

    @Test
    void failsWhenANodeDisappears() {
        reconciler.reconcile();
        engine.status = Map.of();

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
    void stopsOnItsOwnWhenTheTimeLimitIsReached() {
        ExecutionReconciler limited = reconciler(Duration.ZERO);

        limited.reconcile();
        limited.reconcile();
        limited.reconcile();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(engine.calls).containsExactly(UP, DOWN);
    }

    @Test
    void forgetsWhatItSawOnceReleased() {
        reconciler.reconcile();
        reconciler.reconcile();
        execution.requestStop();

        reconciler.reconcile();

        assertThat(observations.of(12L, ORDERS)).isEqualTo(new Observation(null, null));
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
        engine.status = Map.of("c-" + ORDERS, ComponentStatus.HEALTHY, PlanCompiler.LOAD_SERVICE,
                ComponentStatus.EXITED);
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
        engine.status = Map.of("c-" + ORDERS, ComponentStatus.HEALTHY, PlanCompiler.LOAD_SERVICE,
                ComponentStatus.EXITED);
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

    private ExecutionReconciler reconciler(Duration maxDuration) {
        return new ExecutionReconciler(repository, engine, planCompiler, observations, probe, maxDuration,
                mock(PlatformTransactionManager.class));
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
        private Map<String, ComponentStatus> status = Map.of("c-" + ORDERS, ComponentStatus.HEALTHY);
        // k6 exits with 105 when Stop interrupts it.
        private LoadReport loadReport = new LoadReport(105, SUMMARY);

        @Override
        public void up(Map<String, Object> plan) {
            calls.add("up " + plan.get("name"));
            if (upFailure != null) {
                throw upFailure;
            }
        }

        @Override
        public void startLoad(Map<String, Object> plan) {
            calls.add("start-load");
            if (startLoadFailure != null) {
                throw startLoadFailure;
            }
        }

        @Override
        public LoadReport stopLoad(Map<String, Object> plan) {
            calls.add("stop-load");
            if (stopLoadFailure != null) {
                throw stopLoadFailure;
            }
            return loadReport;
        }

        @Override
        public Map<String, ComponentStatus> status(Map<String, Object> plan) {
            if (statusFailure != null) {
                throw statusFailure;
            }
            return status;
        }

        @Override
        public void down(Map<String, Object> plan, Map<String, String> labels) {
            calls.add("down " + plan.get("name") + " " + labels);
            if (downFailure != null) {
                throw downFailure;
            }
        }
    }
}
