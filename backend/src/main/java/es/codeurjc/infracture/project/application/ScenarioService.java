package es.codeurjc.infracture.project.application;

import static es.codeurjc.infracture.project.application.DetailNormalizer.normalizeDescription;
import static es.codeurjc.infracture.project.application.DetailNormalizer.normalizeText;

import es.codeurjc.infracture.catalog.application.ComponentTemplateService;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Connection;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.Workload;
import es.codeurjc.infracture.project.persistence.ScenarioRepository;
import es.codeurjc.infracture.shared.ConflictException;
import es.codeurjc.infracture.shared.NotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final ProjectService projectService;
    private final ComponentTemplateService templateService;
    private final ScenarioValidator scenarioValidator;
    private final ScenarioReservations reservations;
    private final Validator validator;

    public ScenarioService(ScenarioRepository scenarioRepository, ProjectService projectService,
            ComponentTemplateService templateService, ScenarioValidator scenarioValidator,
            ScenarioReservations reservations, Validator validator) {
        this.scenarioRepository = scenarioRepository;
        this.projectService = projectService;
        this.templateService = templateService;
        this.scenarioValidator = scenarioValidator;
        this.reservations = reservations;
        this.validator = validator;
    }

    public Scenario createScenario(String ownerEmail, Long projectId, String name, String description) {
        Project project = projectService.getOwnProject(ownerEmail, projectId);
        Scenario scenario = new Scenario(project, normalizeText(name), normalizeDescription(description));
        validate(scenario);
        return scenarioRepository.save(scenario);
    }

    public Page<Scenario> getScenarios(String ownerEmail, Long projectId, Pageable pageable) {
        Project project = projectService.getOwnProject(ownerEmail, projectId);
        return scenarioRepository.findAllByProjectIdOrderByCreatedAtDescIdDesc(project.getId(), pageable);
    }

    public Scenario getScenario(String ownerEmail, Long projectId, Long id) {
        Project project = projectService.getOwnProject(ownerEmail, projectId);
        return scenarioRepository.findByIdAndProjectId(id, project.getId())
                .orElseThrow(() -> new NotFoundException("Scenario not found"));
    }

    @Transactional
    public Scenario updateScenario(String ownerEmail, Long projectId, Long id, String name, String description) {
        Scenario scenario = lockOwnScenario(ownerEmail, projectId, id);
        requireEditable(scenario);
        scenario.changeDetails(normalizeText(name), normalizeDescription(description));
        validate(scenario);
        return scenarioRepository.saveAndFlush(scenario);
    }

    @Transactional(readOnly = true)
    public ScenarioGraph getGraph(String ownerEmail, Long projectId, Long id) {
        Scenario scenario = getScenario(ownerEmail, projectId, id);
        return new ScenarioGraph(scenario, scenarioValidator.validate(scenario));
    }

    @Transactional
    public ScenarioGraph saveGraph(String ownerEmail, Long projectId, Long id, int expectedRevision,
            List<ComponentDraft> components, List<Connection> connections, Workload workload) {
        Scenario scenario = lockOwnScenario(ownerEmail, projectId, id);
        requireEditable(scenario);
        Set<String> templateKeys = components.stream().map(ComponentDraft::templateKey)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<ComponentDraft> normalized = components.stream()
                .map(draft -> new ComponentDraft(draft.key(), draft.templateKey(), normalizeText(draft.name()),
                        draft.positionX(), draft.positionY(), draft.configuration()))
                .toList();
        scenario.replaceGraph(expectedRevision, normalized, connections, workload,
                templateService.getTemplatesByKey(templateKeys));
        Scenario saved = scenarioRepository.saveAndFlush(scenario);
        return new ScenarioGraph(saved, scenarioValidator.validate(saved));
    }

    // The row lock orders saving and Run on the same scenario: whichever comes second sees the other's result.
    public Scenario lockOwnScenario(String ownerEmail, Long projectId, Long id) {
        Project project = projectService.getOwnProject(ownerEmail, projectId);
        return scenarioRepository.findForUpdateByIdAndProjectId(id, project.getId())
                .orElseThrow(() -> new NotFoundException("Scenario not found"));
    }

    private void requireEditable(Scenario scenario) {
        if (reservations.isReserved(scenario.getId())) {
            throw new ConflictException("The scenario is read-only while an experiment uses it. Stop it to edit the scenario.");
        }
    }

    private void validate(Scenario scenario) {
        Set<ConstraintViolation<Scenario>> violations = validator.validate(scenario);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
