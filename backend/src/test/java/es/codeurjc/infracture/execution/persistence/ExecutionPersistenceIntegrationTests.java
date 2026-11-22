package es.codeurjc.infracture.execution.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedWorkload;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import es.codeurjc.infracture.execution.domain.LoadSummary;
import es.codeurjc.infracture.execution.domain.Outcome;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.domain.StopCause;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Connection;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.Workload;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ExecutionPersistenceIntegrationTests {

    private static final Map<ComponentType, Integer> CONTRACTS = Map.of(
            ComponentType.HTTP_SERVICE, 1, ComponentType.REDIS, 1);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ExecutionRepository repository;

    @Autowired
    private ExecutionEventRepository events;

    private Scenario cache;
    private Scenario queue;
    private final UUID orders = UUID.randomUUID();
    private final UUID redis = UUID.randomUUID();

    @BeforeEach
    void createScenarios() {
        User alice = new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER);
        User bob = new User("bob", "hash", "bob@example.com", "Bob", UserRole.USER);
        entityManager.persist(alice);
        entityManager.persist(bob);
        Project aliceLab = new Project(alice, "Cache lab", null);
        Project bobLab = new Project(bob, "Queue lab", null);
        entityManager.persist(aliceLab);
        entityManager.persist(bobLab);
        cache = new Scenario(aliceLab, "Cold cache", null);
        queue = new Scenario(bobLab, "Tasks", null);
        entityManager.persist(cache);
        entityManager.persist(queue);
        cache.replaceGraph(1, List.of(
                new ComponentDraft(orders, "http-service", "orders-api", 0, 0,
                        new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 50))),
                new ComponentDraft(redis, "redis", "cache", 0, 0, new ComponentConfiguration("DEFAULT", Map.of()))),
                List.of(new Connection(orders, "cache", redis)), null, catalogue());
        entityManager.flush();
    }

    @Test
    void reopensTheFrozenRevisionNodesAndConnections() {
        Execution execution = repository.saveAndFlush(new Execution(cache, CONTRACTS));
        execution.attachPlan(Map.of("name", "infracture-local-exec-" + execution.getId(),
                "services", Map.of("c-" + orders, Map.of("image", "infracture/demo-app:0.1.0"))));
        repository.saveAndFlush(execution);
        entityManager.clear();

        Execution reopened = repository.findById(execution.getId()).orElseThrow();
        assertThat(reopened.getScenarioRevision()).isEqualTo(2);
        assertThat(reopened.getPhase()).isEqualTo(Phase.RESERVED);
        assertThat(reopened.getComponents()).hasSize(2);
        assertThat(reopened.getComponents().getFirst().getId()).isNotNull();
        assertThat(reopened.getComponents().getFirst().getParameters()).containsEntry("responseDelayMs", 50);
        assertThat(reopened.getConnections()).containsExactly(new Connection(orders, "cache", redis));
        assertThat(reopened.getCreatedAt()).isNotNull();
        assertThat(reopened.getPlan()).containsEntry("name", "infracture-local-exec-" + execution.getId())
                .containsKey("services");
        assertThat(repository.findByReleasedAtIsNull()).map(Execution::getId).contains(execution.getId());
    }

    @Test
    void reopensTheFrozenLoadOnItsExecutedTarget() {
        cache.replaceGraph(2, List.of(
                new ComponentDraft(orders, "http-service", "orders-api", 0, 0,
                        new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 50)))), List.of(),
                new Workload(orders, "CONSTANT_HTTP",
                        Map.of("requestsPerSecond", 10, "virtualUsers", 5, "durationSeconds", 60), 42),
                catalogue());
        entityManager.flush();
        Execution execution = repository.saveAndFlush(new Execution(cache, CONTRACTS));
        entityManager.clear();

        Execution reopened = repository.findById(execution.getId()).orElseThrow();
        ExecutedWorkload load = reopened.getWorkload();
        assertThat(load.getTargetKey()).isEqualTo(orders);
        assertThat(load.getProfile()).isEqualTo("CONSTANT_HTTP");
        assertThat(load.getParameters()).containsEntry("requestsPerSecond", 10).hasSize(3);
        assertThat(load.getSeed()).isEqualTo(42);
        assertThat(load.hasSummary()).isFalse();
        assertThat(load.getSummary()).isNull();

        reopened.recordLoadSummary(new LoadSummary(600L, 0L, 3L, 51.5, null), false);
        repository.saveAndFlush(reopened);
        entityManager.clear();

        ExecutedWorkload ended = repository.findById(execution.getId()).orElseThrow().getWorkload();
        assertThat(ended.getSummaryCollectedAt()).isNotNull();
        assertThat(ended.getCompleted()).isFalse();
        assertThat(ended.getSummary()).isEqualTo(new LoadSummary(600L, 0L, 3L, 51.5, null));
    }

    @Test
    void rejectsAFrozenLoadOnANodeOutsideItsExecution() {
        Execution execution = repository.saveAndFlush(new Execution(cache, CONTRACTS));

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO executed_workloads (execution_id, target_key, profile, parameters, seed)
                VALUES (:execution, gen_random_uuid(), 'CONSTANT_HTTP', '{}', 42)
                """).setParameter("execution", execution.getId()).executeUpdate())
                .hasMessageContaining("fk_executed_workloads_target");
    }

    @Test
    void allowsOnlyOneUnreleasedExecutionAcrossAllUsers() {
        repository.saveAndFlush(new Execution(cache, CONTRACTS));

        assertThatThrownBy(() -> repository.saveAndFlush(new Execution(queue, CONTRACTS)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_executions_single_active");
    }

    @Test
    void freesTheReservationOnlyWhenReleased() {
        Execution first = repository.saveAndFlush(new Execution(cache, CONTRACTS));
        first.fail("Image infracture/demo-app:0.1.0 not found");
        first.beginStopping();
        first.release();
        repository.saveAndFlush(first);

        Execution second = repository.saveAndFlush(new Execution(queue, CONTRACTS));
        entityManager.clear();

        Execution released = repository.findById(first.getId()).orElseThrow();
        assertThat(released.getStopCause()).isEqualTo(StopCause.FAILURE);
        assertThat(released.getOutcome()).isEqualTo(Outcome.FAILED);
        assertThat(released.getReleasedAt()).isNotNull();
        assertThat(repository.findByReleasedAtIsNull()).map(Execution::getId).contains(second.getId());
    }

    @Test
    void keepsTheReservationWhileAFailedCleanupWaitsForItsRetry() {
        Execution stopping = repository.saveAndFlush(new Execution(cache, CONTRACTS));
        assertThat(repository.existsByIdAndStopCauseIsNotNull(stopping.getId())).isFalse();
        stopping.requestStop();
        stopping.beginStopping();
        stopping.recordCleanupFailure("docker volume rm failed: volume is in use", Instant.now());
        repository.saveAndFlush(stopping);
        entityManager.clear();

        Execution reopened = repository.findById(stopping.getId()).orElseThrow();
        assertThat(reopened.getCleanupFailures()).isEqualTo(1);
        assertThat(reopened.getCleanupError()).isEqualTo("docker volume rm failed: volume is in use");
        assertThat(reopened.getStopCause()).isEqualTo(StopCause.STOP_REQUESTED);
        assertThat(repository.existsByIdAndStopCauseIsNotNull(stopping.getId())).isTrue();
        assertThatThrownBy(() -> repository.saveAndFlush(new Execution(queue, CONTRACTS)))
                .hasMessageContaining("ux_executions_single_active");
    }

    @Test
    void keepsTheDeadlineAndTheTimeLimitAsTheCause() {
        Execution limited = new Execution(cache, CONTRACTS);
        limited.startStarting();
        limited.markRunning(Instant.parse("2026-11-13T10:00:00Z"), Duration.ofMinutes(30));
        limited.reachTimeLimit();
        limited.beginStopping();
        repository.saveAndFlush(limited);
        entityManager.clear();

        Execution reopened = repository.findById(limited.getId()).orElseThrow();
        assertThat(reopened.getDeadlineAt()).isEqualTo(Instant.parse("2026-11-13T10:30:00Z"));
        assertThat(reopened.getStopCause()).isEqualTo(StopCause.TIME_LIMIT);
        assertThat(repository.findByReleasedAtIsNull()).map(Execution::getId).contains(limited.getId());
    }

    @Test
    void keepsTheInterruptionAndTheReservationUntilTheCleanupEnds() {
        Execution interrupted = new Execution(cache, CONTRACTS);
        interrupted.startStarting();
        interrupted.interrupt(Instant.parse("2026-11-13T10:05:00Z"));
        interrupted.beginStopping();
        repository.saveAndFlush(interrupted);
        entityManager.clear();

        Execution reopened = repository.findById(interrupted.getId()).orElseThrow();
        assertThat(reopened.getStopCause()).isEqualTo(StopCause.INTERRUPTION);
        assertThat(reopened.getInterruptedAt()).isEqualTo(Instant.parse("2026-11-13T10:05:00Z"));
        assertThat(reopened.getInterruptedPhase()).isEqualTo(Phase.STARTING);
        assertThat(repository.findByReleasedAtIsNull()).map(Execution::getId).contains(interrupted.getId());
    }

    @Test
    void rejectsAnInterruptionWithAnotherCause() {
        Execution execution = new Execution(cache, CONTRACTS);
        execution.requestStop();
        repository.saveAndFlush(execution);

        assertThatThrownBy(() -> entityManager.createNativeQuery(
                "UPDATE executions SET interrupted_at = now(), interrupted_phase = 'RESERVED' WHERE id = :id")
                .setParameter("id", execution.getId()).executeUpdate())
                .hasMessageContaining("ck_executions_interruption");
    }

    @Test
    void rejectsARunningRowWithoutDeadline() {
        Execution execution = repository.saveAndFlush(new Execution(cache, CONTRACTS));

        assertThatThrownBy(() -> entityManager.createNativeQuery(
                "UPDATE executions SET phase = 'RUNNING' WHERE id = :id")
                .setParameter("id", execution.getId()).executeUpdate())
                .hasMessageContaining("ck_executions_running_deadline");
    }

    @Test
    void rejectsAReleasedRowWithoutOutcome() {
        Execution execution = repository.saveAndFlush(new Execution(cache, CONTRACTS));

        assertThatThrownBy(() -> entityManager.createNativeQuery(
                "UPDATE executions SET phase = 'RELEASED' WHERE id = :id")
                .setParameter("id", execution.getId()).executeUpdate())
                .hasMessageContaining("ck_executions_released");
    }

    @Test
    void readsEachExecutionsEventsInOrderAfterACursorAndDropsThemWithTheExecution() {
        Execution execution = repository.saveAndFlush(new Execution(cache, CONTRACTS));
        Instant at = Instant.parse("2026-11-21T10:00:00Z");
        ExecutionEvent reserved = events.save(ExecutionEvent.event(execution.getId(), null, ExecutionEvent.PHASE,
                Map.of("phase", "RESERVED"), at));
        ExecutionEvent healthy = events.save(ExecutionEvent.state(execution.getId(), orders, "HEALTHY", at));
        ExecutionEvent line = events.save(ExecutionEvent.log(execution.getId(), orders, "Started", at.plusMillis(5)));
        events.save(ExecutionEvent.log(execution.getId(), null, "k6 running", at.plusMillis(9)));
        events.flush();
        entityManager.clear();

        assertThat(events.findTop200ByExecutionIdAndIdGreaterThanOrderById(execution.getId(), reserved.getId()))
                .extracting(ExecutionEvent::getKind, event -> event.getPayload().get("line"))
                .containsExactly(tuple(ExecutionEvent.Kind.STATE, null), tuple(ExecutionEvent.Kind.LOG, "Started"),
                        tuple(ExecutionEvent.Kind.LOG, "k6 running"));
        assertThat(events.findByExecutionIdAndKindNotOrderById(execution.getId(), ExecutionEvent.Kind.LOG))
                .extracting(ExecutionEvent::getId).containsExactly(reserved.getId(), healthy.getId());
        assertThat(events.findFirstByExecutionIdAndKindOrderByAtDescIdDesc(execution.getId(), ExecutionEvent.Kind.LOG))
                .map(ExecutionEvent::getAt).contains(at.plusMillis(9));
        assertThat(events.countByExecutionIdAndComponentKeyAndKind(execution.getId(), orders, ExecutionEvent.Kind.LOG))
                .isEqualTo(1);
        assertThat(events.countByExecutionIdAndComponentKeyIsNullAndKind(execution.getId(), ExecutionEvent.Kind.LOG))
                .isEqualTo(1);
        assertThat(events.existsByIdAndExecutionId(line.getId(), execution.getId())).isTrue();
        assertThat(events.existsByIdAndExecutionId(line.getId(), execution.getId() + 1)).isFalse();

        entityManager.createNativeQuery("DELETE FROM executions WHERE id = :id")
                .setParameter("id", execution.getId()).executeUpdate();
        assertThat(events.findFirstByExecutionIdOrderByIdDesc(execution.getId())).isEmpty();
    }

    @Test
    void rejectsAnEventOfAnUnknownKind() {
        Execution execution = repository.saveAndFlush(new Execution(cache, CONTRACTS));

        assertThatThrownBy(() -> entityManager.createNativeQuery(
                "INSERT INTO execution_events (execution_id, kind, at, payload) VALUES (:id, 'METRIC', now(), '{}')")
                .setParameter("id", execution.getId()).executeUpdate())
                .hasMessageContaining("ck_execution_events_kind");
    }

    private static Map<String, ComponentTemplate> catalogue() {
        return Map.of(
                "http-service", new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE,
                        true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0))),
                "redis", new ComponentTemplate("redis", "Redis", ComponentType.REDIS, true,
                        new ComponentConfiguration("DEFAULT", Map.of())));
    }
}
