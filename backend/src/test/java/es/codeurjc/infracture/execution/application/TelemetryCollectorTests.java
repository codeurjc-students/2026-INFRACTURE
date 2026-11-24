package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.MetricSample;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.execution.persistence.MetricSampleRepository;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class TelemetryCollectorTests {

    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final UUID USERS = UUID.fromString("0b9c8d7e-6f5a-4b3c-8d2e-1f0a9b8c7d6e");
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private final ExecutionRepository executions = mock(ExecutionRepository.class);
    private final MetricSampleRepository samples = mock(MetricSampleRepository.class);
    private final RuntimeEngine engine = mock(RuntimeEngine.class);
    private final List<MetricSample> stored = new ArrayList<>();
    private final TelemetryCollector collector = new TelemetryCollector(executions, samples, engine,
            Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class));
    private Execution execution;

    @BeforeEach
    void runTwoNodes() {
        execution = new Execution(twoNodes(), Map.of(ComponentType.HTTP_SERVICE, 1));
        ReflectionTestUtils.setField(execution, "id", 12L);
        long id = 100;
        for (ExecutedComponent component : execution.getComponents()) {
            ReflectionTestUtils.setField(component, "id", id++);
        }
        execution.attachPlan(Map.of("name", "infracture-local-exec-12"));
        execution.startStarting();
        execution.markRunning(NOW, Duration.ofMinutes(30));
        when(executions.findByReleasedAtIsNull())
                .thenAnswer(invocation -> execution.isActive() ? Optional.of(execution) : Optional.empty());
        when(executions.findForUpdateById(12L)).thenReturn(Optional.of(execution));
        when(samples.saveAll(anyIterable())).thenAnswer(invocation -> {
            invocation.<Iterable<MetricSample>>getArgument(0).forEach(stored::add);
            return List.of();
        });
    }

    @Test
    void storesOneSamplePerNodeWhileRunning() {
        when(engine.usage(anyMap())).thenReturn(Map.of(
                "c-" + ORDERS, new ResourceUsage(25.5, 1_000L),
                "c-" + USERS, new ResourceUsage(0.0, 2_000L)));

        collector.collect();

        assertThat(stored).extracting(MetricSample::getExecutedComponentId, MetricSample::getSampledAt,
                MetricSample::getCpuPercent, MetricSample::getMemoryBytes)
                .containsExactly(tuple(100L, NOW, 25.5, 1_000L), tuple(101L, NOW, 0.0, 2_000L));
    }

    @Test
    void recordsANodeWithoutAReadingAsAbsent() {
        when(engine.usage(anyMap())).thenReturn(Map.of("c-" + ORDERS, new ResourceUsage(10.0, 500L)));

        collector.collect();

        assertThat(stored).extracting(MetricSample::getExecutedComponentId, MetricSample::getCpuPercent,
                MetricSample::getMemoryBytes)
                .containsExactly(tuple(100L, 10.0, 500L), tuple(101L, null, null));
    }

    // Metrics are evidence, not health: a failed read leaves a gap and the experiment goes on.
    @Test
    void leavesAGapWhenDockerCannotAnswerAndKeepsTheExecutionRunning() {
        when(engine.usage(anyMap())).thenThrow(new RuntimeEngineException("docker stats did not finish"));

        collector.collect();

        assertThat(stored).extracting(MetricSample::getCpuPercent, MetricSample::getMemoryBytes)
                .containsExactly(tuple(null, null), tuple(null, null));
        assertThat(execution.getPhase()).isEqualTo(Phase.RUNNING);
        assertThat(execution.getStopCause()).isNull();
    }

    @Test
    void readsNothingBeforeTheExecutionRuns() {
        execution = new Execution(twoNodes(), Map.of(ComponentType.HTTP_SERVICE, 1));
        execution.startStarting();

        collector.collect();

        verify(engine, never()).usage(anyMap());
        assertThat(stored).isEmpty();
    }

    @Test
    void readsNothingOnceStopIsRequested() {
        execution.requestStop();

        collector.collect();

        verify(engine, never()).usage(anyMap());
        assertThat(stored).isEmpty();
    }

    @Test
    void readsNothingWithoutAnActiveExecution() {
        when(executions.findByReleasedAtIsNull()).thenReturn(Optional.empty());

        collector.collect();

        verify(engine, never()).usage(anyMap());
    }

    @Test
    void discardsTheReadingWhenStopArrivesWhileDockerAnswers() {
        doAnswer(invocation -> {
            execution.requestStop();
            return Map.of("c-" + ORDERS, new ResourceUsage(10.0, 500L));
        }).when(engine).usage(anyMap());

        collector.collect();

        assertThat(stored).isEmpty();
    }

    private static Scenario twoNodes() {
        ComponentConfiguration stateless = new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0));
        ComponentTemplate http = new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE,
                true, stateless);
        Scenario scenario = new Scenario(new Project(
                new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER), "Lab", null), "Run", null);
        scenario.replaceGraph(1, List.of(
                new ComponentDraft(ORDERS, "http-service", "orders-api", 0, 0, stateless),
                new ComponentDraft(USERS, "http-service", "users-api", 200, 0, stateless)),
                List.of(), null, Map.of("http-service", http));
        return scenario;
    }
}
