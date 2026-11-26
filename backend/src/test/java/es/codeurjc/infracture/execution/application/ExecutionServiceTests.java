package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.catalog.application.ContractCatalog;
import es.codeurjc.infracture.catalog.application.ContractCatalogs;
import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.HttpProbe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.domain.StopCause;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.application.ScenarioService;
import es.codeurjc.infracture.project.application.ScenarioValidator;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import es.codeurjc.infracture.project.domain.ValidationIssue.Kind;
import es.codeurjc.infracture.project.domain.ValidationIssue.Severity;
import es.codeurjc.infracture.project.domain.ValidationIssue.Target;
import es.codeurjc.infracture.shared.ConflictException;
import es.codeurjc.infracture.shared.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class ExecutionServiceTests {

    private static final String OWNER = "alice@example.com";
    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final UUID CACHE = UUID.fromString("0b9c8d7e-6f5a-4b3c-8d2e-1f0a9b8c7d6e");
    private static final Map<String, ComponentTemplate> TEMPLATES = Map.of(
            "http-service", new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE, true,
                    new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0))),
            "redis", new ComponentTemplate("redis", "Redis", ComponentType.REDIS, true,
                    new ComponentConfiguration("DEFAULT", Map.of())));

    private final ExecutionRepository repository = mock(ExecutionRepository.class);
    private final ScenarioService scenarioService = mock(ScenarioService.class);
    private final ContractCatalog contracts = ContractCatalogs.standard();
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private final InMemoryExecutionEvents events = new InMemoryExecutionEvents();
    private final ExecutionService service = new ExecutionService(repository, scenarioService,
            new ScenarioValidator(contracts), contracts,
            new PlanCompiler(List.of(new HttpServiceDriver("infracture/demo-app:0.1.0")), "local",
                    "infracture/load-generator:0.1.0"),
            new ExecutionJournal(events.repository, new LogRedactor("", "")), Clock.fixed(NOW, ZoneOffset.UTC));
    private final Project project = new Project(new User("alice", "hash", OWNER, "Alice", UserRole.USER), "Lab", null);

    @Test
    void reservesTheSavedRevisionWithItsContractVersions() {
        Scenario scenario = scenario(3L, stateless());
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario);
        when(repository.findByReleasedAtIsNull()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Execution.class))).thenAnswer(invocation -> {
            Execution saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });

        Execution execution = service.run(OWNER, 5L, 3L, 2).execution();

        assertThat(execution.getPhase()).isEqualTo(Phase.RESERVED);
        assertThat(events.of(12L)).singleElement().satisfies(event -> {
            assertThat(event.getPayload()).isEqualTo(Map.of("type", "PHASE", "phase", "RESERVED"));
            assertThat(event.getAt()).isEqualTo(NOW);
        });
        assertThat(execution.getScenarioRevision()).isEqualTo(2);
        assertThat(execution.getComponents()).singleElement()
                .satisfies(component -> assertThat(component.getContractVersion())
                        .isEqualTo(contracts.find(ComponentType.HTTP_SERVICE).orElseThrow().version()));
        assertThat(execution.getPlan().project()).isEqualTo("infracture-local-exec-12");
    }

    @Test
    void returnsTheSameExecutionWhenTheSameRunArrivesAgain() {
        Scenario scenario = scenario(3L, stateless());
        Execution active = new Execution(scenario, Map.of(ComponentType.HTTP_SERVICE, 1));
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario);
        when(repository.findByReleasedAtIsNull()).thenReturn(Optional.of(active));

        assertThat(service.run(OWNER, 5L, 3L, 2).execution()).isSameAs(active);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsARunWhileAnotherScenarioIsRunning() {
        Execution other = new Execution(scenario(9L, stateless()), Map.of(ComponentType.HTTP_SERVICE, 1));
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario(3L, stateless()));
        when(repository.findByReleasedAtIsNull()).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.run(OWNER, 5L, 3L, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Another experiment is running. Run this scenario when it ends.");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnotherRevisionOfTheScenarioThatIsRunning() {
        Scenario scenario = scenario(3L, stateless());
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario);
        when(repository.findByReleasedAtIsNull())
                .thenReturn(Optional.of(new Execution(scenario, Map.of(ComponentType.HTTP_SERVICE, 1))));

        assertThatThrownBy(() -> service.run(OWNER, 5L, 3L, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This scenario is already running revision 2.");
    }

    @Test
    void rejectsAStaleRevisionWithoutReserving() {
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario(3L, stateless()));
        when(repository.findByReleasedAtIsNull()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.run(OWNER, 5L, 3L, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The scenario was saved after revision 1. Reload it to run revision 2.");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNodesWhoseProfileNoDriverRuns() {
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario(3L, stateless(), redis()));
        when(repository.findByReleasedAtIsNull()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.run(OWNER, 5L, 3L, 2))
                .isInstanceOfSatisfying(ScenarioNotRunnableException.class,
                        exception -> assertThat(exception.getIssues()).containsExactly(new ValidationIssue(
                                Severity.ERROR, "UNSUPPORTED_IN_THIS_VERSION",
                                "cache cannot run yet: this version has no driver for REDIS DEFAULT",
                                new Target(Kind.COMPONENT, CACHE.toString()), "profile")));
        verify(repository, never()).saveAndFlush(any());
    }

    // Run asks the drivers, so a type runs as soon as a driver declares its profile.
    @Test
    void reservesNodesOfAnyProfileADriverDeclares() {
        ExecutionService withRedis = new ExecutionService(repository, scenarioService,
                new ScenarioValidator(contracts), contracts,
                new PlanCompiler(List.of(new HttpServiceDriver("infracture/demo-app:0.1.0"), new RedisDriver()),
                        "local", "infracture/load-generator:0.1.0"),
                new ExecutionJournal(events.repository, new LogRedactor("", "")), Clock.fixed(NOW, ZoneOffset.UTC));
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario(3L, stateless(), redis()));
        when(repository.findByReleasedAtIsNull()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Execution.class))).thenAnswer(invocation -> {
            Execution saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 13L);
            return saved;
        });

        Execution execution = withRedis.run(OWNER, 5L, 3L, 2).execution();

        assertThat(execution.getPlan().nodes()).extracting(Node::image)
                .containsExactlyInAnyOrder("infracture/demo-app:0.1.0", "redis:test");
    }

    @Test
    void rejectsAnEmptyScenario() {
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario(3L));
        when(repository.findByReleasedAtIsNull()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.run(OWNER, 5L, 3L, 2))
                .isInstanceOfSatisfying(ScenarioNotRunnableException.class,
                        exception -> assertThat(exception.getIssues()).extracting(ValidationIssue::code)
                                .containsExactly("EMPTY_SCENARIO"));
    }

    @Test
    void turnsALostRaceForTheReservationIntoAConflict() {
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario(3L, stateless()));
        when(repository.findByReleasedAtIsNull()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Execution.class))).thenThrow(new DataIntegrityViolationException("insert",
                new RuntimeException(
                        "duplicate key value violates unique constraint \"ux_executions_single_active\"")));

        assertThatThrownBy(() -> service.run(OWNER, 5L, 3L, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Another experiment is running. Run this scenario when it ends.");
    }

    @Test
    void keepsOtherIntegrityErrorsUnchanged() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("insert",
                new RuntimeException("violates check constraint \"ck_executions_phase\""));
        when(scenarioService.lockOwnScenario(OWNER, 5L, 3L)).thenReturn(scenario(3L, stateless()));
        when(repository.findByReleasedAtIsNull()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Execution.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.run(OWNER, 5L, 3L, 2)).isSameAs(failure);
    }

    @Test
    void recordsStopWithoutTouchingDocker() {
        Scenario scenario = scenario(3L, stateless());
        Execution active = new Execution(scenario, Map.of(ComponentType.HTTP_SERVICE, 1));
        ReflectionTestUtils.setField(active, "id", 12L);
        when(scenarioService.getScenario(OWNER, 5L, 3L)).thenReturn(scenario);
        when(repository.findForUpdateById(12L)).thenReturn(Optional.of(active));

        Execution stopped = service.stop(OWNER, 5L, 3L, 12L).execution();
        service.stop(OWNER, 5L, 3L, 12L);

        assertThat(stopped.getStopCause()).isEqualTo(StopCause.STOP_REQUESTED);
        assertThat(stopped.getPhase()).isEqualTo(Phase.RESERVED);
        // A repeated Stop changes nothing, so it records nothing either.
        assertThat(events.of(12L)).singleElement().extracting(event -> event.getPayload())
                .isEqualTo(Map.of("type", "STOP", "cause", "STOP_REQUESTED"));
    }

    @Test
    void hidesExecutionsOfOtherScenariosFromStop() {
        when(scenarioService.getScenario(OWNER, 5L, 3L)).thenReturn(scenario(3L, stateless()));
        when(repository.findForUpdateById(12L))
                .thenReturn(
                        Optional.of(new Execution(scenario(9L, stateless()), Map.of(ComponentType.HTTP_SERVICE, 1))));

        assertThatThrownBy(() -> service.stop(OWNER, 5L, 3L, 12L))
                .isInstanceOf(NotFoundException.class).hasMessage("Execution not found");
        assertThatThrownBy(() -> service.stop(OWNER, 5L, 3L, 13L))
                .isInstanceOf(NotFoundException.class).hasMessage("Execution not found");
    }

    @Test
    void findsTheLatestExecutionOfAnOwnScenario() {
        Scenario scenario = scenario(3L, stateless());
        Execution latest = new Execution(scenario, Map.of(ComponentType.HTTP_SERVICE, 1));
        when(scenarioService.getScenario(OWNER, 5L, 3L)).thenReturn(scenario);
        when(repository.findFirstByScenarioIdOrderByCreatedAtDescIdDesc(3L)).thenReturn(Optional.of(latest));

        assertThat(service.getCurrent(OWNER, 5L, 3L)).get().extracting(ExecutionView::execution).isSameAs(latest);
    }

    private Scenario scenario(Long id, ComponentDraft... nodes) {
        Scenario scenario = new Scenario(project, "Run", null);
        ReflectionTestUtils.setField(scenario, "id", id);
        scenario.replaceGraph(1, List.of(nodes), List.of(), null, TEMPLATES);
        return scenario;
    }

    private static ComponentDraft stateless() {
        return new ComponentDraft(ORDERS, "http-service", "orders-api", 0, 0,
                new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)));
    }

    private static final class RedisDriver implements ComponentDriver {

        @Override
        public ComponentType type() {
            return ComponentType.REDIS;
        }

        @Override
        public Set<String> profiles() {
            return Set.of("DEFAULT");
        }

        @Override
        public Service service(ExecutedComponent component) {
            return new Service("redis:test", null, Map.of(), new HttpProbe(6379, "/", 2, 2, 15, 60));
        }
    }

    private static ComponentDraft redis() {
        return new ComponentDraft(CACHE, "redis", "cache", 0, 0, new ComponentConfiguration("DEFAULT", Map.of()));
    }
}
