package es.codeurjc.infracture.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.Workload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecutionTests {

    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final Map<ComponentType, Integer> CONTRACTS = Map.of(ComponentType.HTTP_SERVICE, 1);
    private static final Instant STARTED = Instant.parse("2026-11-13T10:00:00Z");
    private static final Duration LIMIT = Duration.ofMinutes(30);

    private final Scenario scenario = statelessScenario();
    private final Execution execution = new Execution(scenario, CONTRACTS);

    @Test
    void freezesTheRevisionAndEachNodeWhenReserved() {
        assertThat(execution.getPhase()).isEqualTo(Phase.RESERVED);
        assertThat(execution.getScenarioRevision()).isEqualTo(2);
        assertThat(execution.isActive()).isTrue();
        assertThat(execution.getComponents()).singleElement().satisfies(component -> {
            assertThat(component.getKey()).isEqualTo(ORDERS);
            assertThat(component.getName()).isEqualTo("orders-api");
            assertThat(component.getContractVersion()).isEqualTo(1);
            assertThat(component.getProfile()).isEqualTo("STATELESS");
            assertThat(component.getParameters()).containsEntry("responseDelayMs", 50);
        });

        scenario.replaceGraph(2, List.of(), List.of(), null, Map.of());

        assertThat(execution.getComponents()).hasSize(1);
        assertThat(execution.getScenarioRevision()).isEqualTo(2);
    }

    @Test
    void freezesTheLoadWhenReservedAndRunsWithoutOneWhenTheScenarioHasNone() {
        assertThat(execution.getWorkload()).isNull();
        Workload load = new Workload(ORDERS, "CONSTANT_HTTP",
                Map.of("requestsPerSecond", 10, "virtualUsers", 5, "durationSeconds", 60), 42);
        scenario.replaceGraph(2, List.of(orders()), List.of(), load, templates());

        Execution loaded = new Execution(scenario, CONTRACTS);
        scenario.replaceGraph(3, List.of(orders()), List.of(), null, templates());

        assertThat(loaded.getWorkload().getTargetKey()).isEqualTo(ORDERS);
        assertThat(loaded.getWorkload().getProfile()).isEqualTo("CONSTANT_HTTP");
        assertThat(loaded.getWorkload().getParameters())
                .isEqualTo(Map.of("requestsPerSecond", 10, "virtualUsers", 5, "durationSeconds", 60));
        assertThat(loaded.getWorkload().getSeed()).isEqualTo(42);
    }

    @Test
    void keepsTheFirstLoadSummaryAndRefusesOneWithoutLoad() {
        scenario.replaceGraph(2, List.of(orders()), List.of(), new Workload(ORDERS, "CONSTANT_HTTP",
                Map.of("requestsPerSecond", 10, "virtualUsers", 5, "durationSeconds", 60), 42), templates());
        Execution loaded = new Execution(scenario, CONTRACTS);
        LoadSummary first = new LoadSummary(600L, 2L, 0L, 51.0, 60.0);

        loaded.recordLoadSummary(first, true);
        loaded.recordLoadSummary(new LoadSummary(1L, null, null, null, null), false);

        assertThat(loaded.getWorkload().getSummary()).isEqualTo(first);
        assertThat(loaded.getWorkload().getCompleted()).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> execution.recordLoadSummary(first, true));
    }

    @Test
    void completesWhenTheOnlyCauseIsStop() {
        execution.startStarting();
        execution.markRunning(STARTED, LIMIT);
        execution.requestStop();
        execution.requestStop();
        execution.beginStopping();
        execution.release();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(execution.isActive()).isFalse();
        assertThat(execution.getReleasedAt()).isNotNull();
    }

    @Test
    void stopsStraightFromTheReservation() {
        execution.requestStop();
        execution.beginStopping();
        execution.release();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
    }

    // Path found by the TLA+ model: with "the first cause wins" this ended as success.
    @Test
    void failsWhenStartUpFailsAfterStopWasRequested() {
        execution.startStarting();
        execution.requestStop();
        execution.fail("Container orders-api is unhealthy");
        execution.beginStopping();
        execution.release();

        assertThat(execution.getStopCause()).isEqualTo(StopCause.FAILURE);
        assertThat(execution.getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(execution.getFailureReason()).isEqualTo("Container orders-api is unhealthy");
    }

    // Second path found by the TLA+ model: a crash during the cleanup of a Stop.
    @Test
    void isInterruptedWhenTheBackendStopsDuringTheCleanupOfAStop() {
        execution.startStarting();
        execution.markRunning(STARTED, LIMIT);
        execution.requestStop();
        execution.beginStopping();
        execution.interrupt();
        execution.requestStop();
        execution.release();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.INTERRUPTED);
    }

    @Test
    void startsTheClockWhenRunningAndCompletesAtTheTimeLimit() {
        execution.startStarting();
        assertThat(execution.getDeadlineAt()).isNull();
        assertThat(execution.isPastDeadline(STARTED.plus(Duration.ofDays(1)))).isFalse();

        execution.markRunning(STARTED, LIMIT);

        assertThat(execution.getDeadlineAt()).isEqualTo(Instant.parse("2026-11-13T10:30:00Z"));
        assertThat(execution.isPastDeadline(Instant.parse("2026-11-13T10:29:59Z"))).isFalse();
        assertThat(execution.isPastDeadline(Instant.parse("2026-11-13T10:30:00Z"))).isTrue();
        execution.reachTimeLimit();
        execution.beginStopping();
        execution.release();
        assertThat(execution.getStopCause()).isEqualTo(StopCause.TIME_LIMIT);
        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
    }

    @Test
    void keepsTheTimeLimitAsTheCauseWhenStopFollowsButLetsAFailureWin() {
        execution.startStarting();
        execution.markRunning(STARTED, LIMIT);
        execution.reachTimeLimit();
        execution.requestStop();

        assertThat(execution.getStopCause()).isEqualTo(StopCause.TIME_LIMIT);
        execution.fail("docker compose stop failed: Cannot connect");
        execution.beginStopping();
        execution.release();
        assertThat(execution.getOutcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    void countsTheSecondsLeftOnlyWhileTheExperimentRuns() {
        assertThat(execution.secondsLeft(STARTED)).isNull();
        execution.startStarting();
        execution.markRunning(STARTED, LIMIT);

        assertThat(execution.secondsLeft(Instant.parse("2026-11-13T10:00:10.400Z"))).isEqualTo(1789);
        assertThat(execution.secondsLeft(Instant.parse("2026-11-13T10:45:00Z"))).isZero();
        execution.requestStop();
        assertThat(execution.secondsLeft(STARTED)).isNull();
    }

    @Test
    void keepsTheFirstFailureReasonAndShortensLongOnes() {
        execution.fail("x".repeat(1200));
        execution.fail("a later consequence");

        assertThat(execution.getFailureReason()).hasSize(1000);
    }

    @Test
    void ignoresCausesAfterRelease() {
        execution.requestStop();
        execution.beginStopping();
        execution.release();

        execution.fail("late");
        execution.interrupt();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(execution.getStopCause()).isEqualTo(StopCause.STOP_REQUESTED);
        assertThat(execution.getFailureReason()).isNull();
    }

    @Test
    void tracksAFailedCleanupWithoutChangingHowTheExecutionEnds() {
        execution.requestStop();
        execution.beginStopping();

        execution.recordCleanupFailure("docker volume rm failed: " + "x".repeat(1200), Instant.now());
        execution.release();

        assertThat(execution.getCleanupFailures()).isEqualTo(1);
        assertThat(execution.getCleanupError()).startsWith("docker volume rm failed").hasSize(1000);
        assertThat(execution.getStopCause()).isEqualTo(StopCause.STOP_REQUESTED);
        assertThat(execution.getOutcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(execution.getFailureReason()).isNull();
    }

    @Test
    void waitsLongerAfterEachFailedCleanupAndThenForTheOwner() {
        Duration pause = Duration.ofSeconds(10);
        execution.requestStop();
        execution.beginStopping();
        assertThat(execution.isCleanupDue(Instant.now(), pause)).isTrue();

        execution.recordCleanupFailure("first", Instant.now());
        Instant now = Instant.now();
        assertThat(execution.isCleanupDue(now, pause)).isFalse();
        assertThat(execution.isCleanupDue(now.plusSeconds(10), pause)).isTrue();

        execution.recordCleanupFailure("second", Instant.now());
        now = Instant.now();
        assertThat(execution.isCleanupDue(now.plusSeconds(10), pause)).isFalse();
        assertThat(execution.isCleanupDue(now.plusSeconds(20), pause)).isTrue();

        execution.recordCleanupFailure("third", Instant.now());
        assertThat(execution.isCleanupRetryNeeded()).isTrue();
        assertThat(execution.isCleanupDue(Instant.now().plus(Duration.ofDays(1)), pause)).isFalse();
    }

    @Test
    void asksForNewCleanupAttemptsWhenStopIsPressedAgain() {
        execution.requestStop();
        execution.beginStopping();
        for (int attempt = 0; attempt < Execution.AUTOMATIC_CLEANUP_ATTEMPTS; attempt++) {
            execution.recordCleanupFailure("docker network rm failed", Instant.now());
        }

        execution.requestStop();

        assertThat(execution.isCleanupRetryNeeded()).isFalse();
        assertThat(execution.isCleanupDue(Instant.now(), Duration.ofSeconds(10))).isTrue();
        assertThat(execution.getCleanupError()).isEqualTo("docker network rm failed");
    }

    @Test
    void recordsCleanupFailuresOnlyWhileStopping() {
        assertThatIllegalStateException().isThrownBy(() -> execution.recordCleanupFailure("too early", Instant.now()));
    }

    @Test
    void acceptsThePlanOnlyOnce() {
        execution.attachPlan(Map.of("name", "infracture-local-exec-12"));

        assertThat(execution.getPlan()).containsEntry("name", "infracture-local-exec-12");
        assertThatIllegalStateException().isThrownBy(() -> execution.attachPlan(Map.of()));
    }

    @Test
    void rejectsTransitionsOutOfOrder() {
        assertThatIllegalStateException().isThrownBy(() -> execution.markRunning(STARTED, LIMIT));
        assertThatIllegalStateException().isThrownBy(execution::beginStopping);
        assertThatIllegalStateException().isThrownBy(execution::release);

        execution.startStarting();
        assertThatIllegalStateException().isThrownBy(execution::startStarting);
    }

    private static Scenario statelessScenario() {
        Scenario scenario = new Scenario(new Project(
                new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER), "Lab", null), "Run", null);
        scenario.replaceGraph(1, List.of(orders()), List.of(), null, templates());
        return scenario;
    }

    private static ComponentDraft orders() {
        return new ComponentDraft(ORDERS, "http-service", "orders-api", 0, 0,
                new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 50)));
    }

    private static Map<String, ComponentTemplate> templates() {
        return Map.of("http-service", new ComponentTemplate("http-service", "HTTP Service",
                ComponentType.HTTP_SERVICE, true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0))));
    }
}
