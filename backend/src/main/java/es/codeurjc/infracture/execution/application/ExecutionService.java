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

    public ExecutionService(ExecutionRepository repository, ScenarioService scenarioService,
            ScenarioValidator validator, ContractCatalog contracts, PlanCompiler planCompiler) {
        this.repository = repository;
        this.scenarioService = scenarioService;
        this.validator = validator;
        this.contracts = contracts;
        this.planCompiler = planCompiler;
    }

    // Reserves the exact revision the client saved. Docker is not touched here: the
    // engine starts the
    // reserved execution after this transaction commits, so no database transaction
    // waits for Docker.
    @Transactional
    public Execution run(String ownerEmail, Long projectId, Long scenarioId, int revision) {
        Scenario scenario = scenarioService.lockOwnScenario(ownerEmail, projectId, scenarioId);
        Optional<Execution> active = repository.findByReleasedAtIsNull();
        // Checks if a scenario is already running and if it is the same revision that
        // the user wants to run. If it is not the same revision, it throws a conflict
        // exception.
        if (active.isPresent()) {
            return sameRun(active.get(), scenario, revision);
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
        return execution;
    }

    @Transactional(readOnly = true)
    public Optional<Execution> getCurrent(String ownerEmail, Long projectId, Long scenarioId) {
        Scenario scenario = scenarioService.getScenario(ownerEmail, projectId, scenarioId);
        return repository.findFirstByScenarioIdOrderByCreatedAtDescIdDesc(scenario.getId());
    }

    // Stop only records the cause; the reconciler cleans up afterwards, so the
    // request returns at once.
    // Repeating it changes nothing, including after the execution was released.
    @Transactional
    public Execution stop(String ownerEmail, Long projectId, Long scenarioId, Long executionId) {
        Scenario scenario = scenarioService.getScenario(ownerEmail, projectId, scenarioId);
        Optional<Execution> found = repository.findForUpdateById(executionId);
        if (found.isEmpty() || !found.get().getScenario().getId().equals(scenario.getId())) {
            throw new NotFoundException("Execution not found");
        }
        Execution execution = found.get();
        execution.requestStop();
        return execution;
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
        // shortcut: only HTTP Service STATELESS has a driver in #86; move this to the
        // plan compiler with P07.
        for (Component component : scenario.getComponents()) {
            if (component.getType() != ComponentType.HTTP_SERVICE
                    || !"STATELESS".equals(component.getConfiguration().profile())) {
                errors.add(new ValidationIssue(Severity.ERROR, "UNSUPPORTED_IN_THIS_VERSION",
                        "%s cannot run yet: this version runs HTTP Service STATELESS only"
                                .formatted(component.getName()),
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
