package es.codeurjc.infracture.execution.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.Outcome;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.domain.StopCause;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Connection;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import jakarta.persistence.EntityManager;
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
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ExecutionRepository repository;

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
                List.of(new Connection(orders, "cache", redis)), catalogue());
        entityManager.flush();
    }

    @Test
    void reopensTheFrozenRevisionNodesAndConnections() {
        Execution execution = repository.saveAndFlush(new Execution(cache, CONTRACTS));
        entityManager.clear();

        Execution reopened = repository.findById(execution.getId()).orElseThrow();
        assertThat(reopened.getScenarioRevision()).isEqualTo(2);
        assertThat(reopened.getPhase()).isEqualTo(Phase.RESERVED);
        assertThat(reopened.getComponents()).hasSize(2);
        assertThat(reopened.getComponents().getFirst().getId()).isNotNull();
        assertThat(reopened.getComponents().getFirst().getParameters()).containsEntry("responseDelayMs", 50);
        assertThat(reopened.getConnections()).containsExactly(new Connection(orders, "cache", redis));
        assertThat(reopened.getCreatedAt()).isNotNull();
        assertThat(repository.findByReleasedAtIsNull()).map(Execution::getId).contains(execution.getId());
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
    void rejectsAReleasedRowWithoutOutcome() {
        Execution execution = repository.saveAndFlush(new Execution(cache, CONTRACTS));

        assertThatThrownBy(() -> entityManager.createNativeQuery(
                "UPDATE executions SET phase = 'RELEASED' WHERE id = :id")
                .setParameter("id", execution.getId()).executeUpdate())
                .hasMessageContaining("ck_executions_released");
    }

    private static Map<String, ComponentTemplate> catalogue() {
        return Map.of(
                "http-service", new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE,
                        true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0))),
                "redis", new ComponentTemplate("redis", "Redis", ComponentType.REDIS, true,
                        new ComponentConfiguration("DEFAULT", Map.of())));
    }
}
