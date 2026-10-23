package es.codeurjc.infracture.project.application;

import static es.codeurjc.infracture.project.application.DetailNormalizer.normalizeDescription;
import static es.codeurjc.infracture.project.application.DetailNormalizer.normalizeText;

import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.persistence.ScenarioRepository;
import es.codeurjc.infracture.shared.NotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final ProjectService projectService;
    private final Validator validator;

    public ScenarioService(ScenarioRepository scenarioRepository, ProjectService projectService, Validator validator) {
        this.scenarioRepository = scenarioRepository;
        this.projectService = projectService;
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
        Scenario scenario = getScenario(ownerEmail, projectId, id);
        scenario.changeDetails(normalizeText(name), normalizeDescription(description));
        validate(scenario);
        return scenarioRepository.saveAndFlush(scenario);
    }

    private void validate(Scenario scenario) {
        Set<ConstraintViolation<Scenario>> violations = validator.validate(scenario);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
