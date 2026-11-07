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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecutionTests {

    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final Map<ComponentType, Integer> CONTRACTS = Map.of(ComponentType.HTTP_SERVICE, 1);

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

        scenario.replaceGraph(2, List.of(), List.of(), Map.of());

        assertThat(execution.getComponents()).hasSize(1);
        assertThat(execution.getScenarioRevision()).isEqualTo(2);
    }

    @Test
    void completesWhenTheOnlyCauseIsStop() {
        execution.startStarting();
        execution.markRunning();
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
        execution.markRunning();
        execution.requestStop();
        execution.beginStopping();
        execution.interrupt();
        execution.requestStop();
        execution.release();

        assertThat(execution.getOutcome()).isEqualTo(Outcome.INTERRUPTED);
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
    void acceptsThePlanOnlyOnce() {
        execution.attachPlan(Map.of("name", "infracture-local-exec-12"));

        assertThat(execution.getPlan()).containsEntry("name", "infracture-local-exec-12");
        assertThatIllegalStateException().isThrownBy(() -> execution.attachPlan(Map.of()));
    }

    @Test
    void rejectsTransitionsOutOfOrder() {
        assertThatIllegalStateException().isThrownBy(execution::markRunning);
        assertThatIllegalStateException().isThrownBy(execution::beginStopping);
        assertThatIllegalStateException().isThrownBy(execution::release);

        execution.startStarting();
        assertThatIllegalStateException().isThrownBy(execution::startStarting);
    }

    private static Scenario statelessScenario() {
        ComponentTemplate http = new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE,
                true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)));
        Scenario scenario = new Scenario(new Project(
                new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER), "Lab", null), "Run", null);
        scenario.replaceGraph(1, List.of(new ComponentDraft(ORDERS, "http-service", "orders-api", 0, 0,
                new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 50)))), List.of(),
                Map.of("http-service", http));
        return scenario;
    }
}
