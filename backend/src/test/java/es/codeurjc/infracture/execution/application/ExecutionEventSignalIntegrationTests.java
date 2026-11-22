package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// The notices only leave PostgreSQL when a transaction commits, so these tests commit for real.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ExecutionEventSignalIntegrationTests {

    private static final Duration PATIENCE = Duration.ofSeconds(10);
    private static final Long UNWATCHED_ELSEWHERE = 999_999L;
    private static final AtomicInteger ACCOUNTS = new AtomicInteger();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @Autowired
    private JdbcConnectionDetails database;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    private ExecutionEventSignal signal;
    private Long executionId;

    @BeforeEach
    void reserveAndListen() {
        executionId = new TransactionTemplate(transactions).execute(status -> {
            String name = "listener_" + ACCOUNTS.incrementAndGet();
            User owner = new User(name, "hash", name + "@example.com", "Owner", UserRole.USER);
            entityManager.persist(owner);
            Project lab = new Project(owner, "Lab", null);
            entityManager.persist(lab);
            Scenario scenario = new Scenario(lab, "Run", null);
            entityManager.persist(scenario);
            ComponentConfiguration stateless = new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0));
            scenario.replaceGraph(1, List.of(new ComponentDraft(UUID.randomUUID(), "http-service", "orders-api", 0,
                    0, stateless)), List.of(), null, Map.of("http-service",
                            new ComponentTemplate("http-service",
                                    "HTTP Service", ComponentType.HTTP_SERVICE, true, stateless)));
            Execution execution = new Execution(scenario, Map.of(ComponentType.HTTP_SERVICE, 1));
            entityManager.persist(execution);
            entityManager.flush();
            return execution.getId();
        });
        signal = new ExecutionEventSignal(database);
        signal.start();
        waitUntil(signal::isListening);
    }

    @AfterEach
    void stopListening() {
        if (signal != null) {
            signal.stop();
        }
        jdbc.update("DELETE FROM executions WHERE id = ?", executionId);
    }

    @Test
    void wakesTheStreamsOfAnExecutionWhenItsEventsCommit() throws InterruptedException {
        signal.watch(executionId);
        signal.watch(UNWATCHED_ELSEWHERE);
        long seen = signal.version(executionId);
        long other = signal.version(UNWATCHED_ELSEWHERE);

        commitEvents(3);
        long started = System.nanoTime();
        signal.await(executionId, seen, PATIENCE);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(PATIENCE);
        assertThat(signal.version(executionId)).isNotEqualTo(seen);
        assertThat(signal.version(UNWATCHED_ELSEWHERE)).isEqualTo(other);
    }

    @Test
    void waitsNoLongerThanAskedWhenNothingHappens() throws InterruptedException {
        signal.watch(executionId);
        long seen = signal.version(executionId);

        signal.await(executionId, seen, Duration.ofMillis(200));

        assertThat(signal.version(executionId)).isEqualTo(seen);
    }

    @Test
    void listensAgainAfterLosingItsConnectionAndWakesEveryStreamToReadWhatItMissed() throws InterruptedException {
        signal.watch(executionId);
        long beforeLoss = signal.version(executionId);

        jdbc.queryForList("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE application_name = ?",
                ExecutionEventSignal.APPLICATION_NAME);
        waitUntil(() -> !signal.isListening());
        waitUntil(signal::isListening);

        assertThat(signal.version(executionId)).isNotEqualTo(beforeLoss);
        long seen = signal.version(executionId);
        commitEvents(1);
        signal.await(executionId, seen, PATIENCE);
        assertThat(signal.version(executionId)).isNotEqualTo(seen);
    }

    @Test
    void forgetsAnExecutionOnceNoStreamWatchesIt() {
        signal.watch(executionId);
        signal.watch(executionId);
        commitEvents(1);
        waitUntil(() -> signal.version(executionId) > versionWithoutNotices());

        signal.unwatch(executionId);
        assertThat(signal.version(executionId)).isNotEqualTo(versionWithoutNotices());
        signal.unwatch(executionId);
        assertThat(signal.version(executionId)).isEqualTo(versionWithoutNotices());
    }

    // The version of an execution nobody watches: only the reconnections count.
    private long versionWithoutNotices() {
        return signal.version(UNWATCHED_ELSEWHERE);
    }

    private void commitEvents(int count) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (int line = 0; line < count; line++) {
                entityManager.persist(ExecutionEvent.log(executionId, null, "line " + line, Instant.now()));
            }
        });
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("waited %s", PATIENCE).isLessThan(deadline);
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
    }
}
