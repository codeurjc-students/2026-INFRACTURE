package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.application.ContractCatalog;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.project.application.ScenarioService;
import es.codeurjc.infracture.project.application.ScenarioValidator;
import es.codeurjc.infracture.project.domain.Component;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import es.codeurjc.infracture.project.domain.ValidationIssue.Kind;
import es.codeurjc.infracture.project.domain.ValidationIssue.Severity;
import es.codeurjc.infracture.project.domain.ValidationIssue.Target;
import es.codeurjc.infracture.shared.ConflictException;
import es.codeurjc.infracture.shared.NotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionService {

    private static final String SINGLE_ACTIVE_INDEX = "ux_executions_single_active";
    private static final String ANOTHER_RUNNING = "Another experiment is running. Run this scenario when it ends.";

    private final ExecutionRepository repository;
    private final ScenarioService scenarioService;
    private final ScenarioValidator validator;
    private final ContractCatalog contracts;
    private final PlanCompiler planCompiler;
    private final ExecutionJournal journal;
    private final Clock clock;

    public ExecutionService(ExecutionRepository repository, ScenarioService scenarioService,
            ScenarioValidator validator, ContractCatalog contracts, PlanCompiler planCompiler,
            ExecutionJournal journal, Clock clock) {
        this.repository = repository;
        this.scenarioService = scenarioService;
        this.validator = validator;
        this.contracts = contracts;
        this.planCompiler = planCompiler;
        this.journal = journal;
        this.clock = clock;
    }

    // Reserves the exact revision the client saved. Docker is not touched here: the
    // engine starts the
    // reserved execution after this transaction commits, so no database transaction
    // waits for Docker.
    @Transactional
    public ExecutionView run(String ownerEmail, Long projectId, Long scenarioId, int revision) {
        Scenario scenario = scenarioService.lockOwnScenario(ownerEmail, projectId, scenarioId);
        Optional<Execution> active = repository.findByReleasedAtIsNull();
        // Checks if a scenario is already running and if it is the same revision that
        // the user wants to run. If it is not the same revision, it throws a conflict
        // exception.
        if (active.isPresent()) {
            return view(sameRun(active.get(), scenario, revision));
        }
        // if not active but you press Run with a different revision, it throws a
        // conflict exception.
        if (scenario.getRevision() != revision) {
            throw new ConflictException("The scenario was saved after revision %d. Reload it to run revision %d."
                    .formatted(revision, scenario.getRevision()));
        }
        // Checks if the scenario has any blocking issues. If it does, it throws a
        // ScenarioNotRunnableException with the list of issues.
        List<ValidationIssue> errors = blockingIssues(scenario);
        if (!errors.isEmpty()) {
            throw new ScenarioNotRunnableException(errors);
        }

        // Saves the execution with the reservation. The engine starts it after this
        // transaction commits, so no database transaction waits for Docker.
        Execution execution;
        try {
            execution = repository.saveAndFlush(new Execution(scenario, contractVersions(scenario)));
        } catch (DataIntegrityViolationException exception) {
            // Another scenario reserved the instance between the check above and this
            // insert.
            if (String.valueOf(exception.getMostSpecificCause().getMessage()).contains(SINGLE_ACTIVE_INDEX)) {
                throw new ConflictException(ANOTHER_RUNNING);
            }
            throw exception;
        }
        // Saved with the reservation when the transaction commits.
        execution.attachPlan(planCompiler.compile(execution));
        journal.recordReserved(execution, clock.instant());
        return view(execution);
    }

    @Transactional(readOnly = true)
    public Optional<ExecutionView> getCurrent(String ownerEmail, Long projectId, Long scenarioId) {
        Scenario scenario = scenarioService.getScenario(ownerEmail, projectId, scenarioId);
        return repository.findFirstByScenarioIdOrderByCreatedAtDescIdDesc(scenario.getId()).map(this::view);
    }

    // One execution of the owner's scenario; another account's, or one of another scenario, is not found.
    @Transactional(readOnly = true)
    public ExecutionView getOwn(String ownerEmail, Long projectId, Long scenarioId, Long executionId) {
        return view(ownExecution(ownerEmail, projectId, scenarioId, executionId, false));
    }

    // Stop only records the cause; the reconciler cleans up afterwards, so the
    // request returns at once.
    // Repeating it changes nothing, including after the execution was released.
    @Transactional
    public ExecutionView stop(String ownerEmail, Long projectId, Long scenarioId, Long executionId) {
        Execution execution = ownExecution(ownerEmail, projectId, scenarioId, executionId, true);
        ExecutionJournal.Mark before = ExecutionJournal.mark(execution);
        execution.requestStop();
        journal.recordChanges(before, execution, clock.instant());
        return view(execution);
    }

    public Execution ownExecution(String ownerEmail, Long projectId, Long scenarioId, Long executionId,
            boolean lock) {
        Scenario scenario = scenarioService.getScenario(ownerEmail, projectId, scenarioId);
        Optional<Execution> found = lock ? repository.findForUpdateById(executionId) : repository.findById(executionId);
        if (found.isEmpty() || !found.get().getScenario().getId().equals(scenario.getId())) {
            throw new NotFoundException("Execution not found");
        }
        return found.get();
    }

    // Read inside the transaction: the frozen nodes and load are lazy, and the web layer has no session.
    // getComponents() wraps the lazy list, so Hibernate.initialize cannot see it; size() reaches it and
    // loads it.
    @SuppressWarnings("java:S2201")
    private ExecutionView view(Execution execution) {
        execution.getComponents().size();
        execution.getWorkload();
        return new ExecutionView(execution, journal.nodes(execution.getId()), execution.secondsLeft(clock.instant()));
    }

    // A double click or a retry after a lost response gets the execution it already
    // created.
    private static Execution sameRun(Execution active, Scenario scenario, int revision) {
        if (!active.getScenario().getId().equals(scenario.getId())) {
            throw new ConflictException(ANOTHER_RUNNING);
        }
        if (active.getScenarioRevision() != revision) {
            throw new ConflictException("This scenario is already running revision %d."
                    .formatted(active.getScenarioRevision()));
        }
        return active;
    }

    private List<ValidationIssue> blockingIssues(Scenario scenario) {
        List<ValidationIssue> errors = new ArrayList<>();
        for (ValidationIssue issue : validator.validate(scenario)) {
            if (issue.severity() == Severity.ERROR) {
                errors.add(issue);
            }
        }
        // A valid node still needs a driver for its profile; the drivers say which ones they run.
        for (Component component : scenario.getComponents()) {
            String profile = component.getConfiguration().profile();
            if (!planCompiler.runs(component.getType(), profile)) {
                errors.add(new ValidationIssue(Severity.ERROR, "UNSUPPORTED_IN_THIS_VERSION",
                        "%s cannot run yet: this version has no driver for %s %s"
                                .formatted(component.getName(), component.getType(), profile),
                        new Target(Kind.COMPONENT, component.getKey().toString()), "profile"));
            }
        }
        return errors;
    }

    private Map<ComponentType, Integer> contractVersions(Scenario scenario) {
        Map<ComponentType, Integer> versions = new EnumMap<>(ComponentType.class);
        for (Component component : scenario.getComponents()) {
            versions.put(component.getType(), contracts.find(component.getType()).orElseThrow().version());
        }
        return versions;
    }
}
