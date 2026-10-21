package es.codeurjc.infracture.project.application;

import es.codeurjc.infracture.identity.application.UserService;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.persistence.ProjectRepository;
import es.codeurjc.infracture.shared.NotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.text.Normalizer;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;
    private final Validator validator;

    public ProjectService(ProjectRepository projectRepository, UserService userService, Validator validator) {
        this.projectRepository = projectRepository;
        this.userService = userService;
        this.validator = validator;
    }

    public Project createProject(String ownerEmail, String name, String description) {
        User owner = userService.getUserByEmail(ownerEmail);
        Project project = new Project(owner, normalizeText(name), normalizeDescription(description));
        validate(project);
        return projectRepository.save(project);
    }

    public Page<Project> getOwnProjects(String ownerEmail, Pageable pageable) {
        User owner = userService.getUserByEmail(ownerEmail);
        return projectRepository.findAllByOwnerIdOrderByCreatedAtDescIdDesc(owner.getId(), pageable);
    }

    public Project getOwnProject(String ownerEmail, Long id) {
        User owner = userService.getUserByEmail(ownerEmail);
        return projectRepository.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    @Transactional
    public Project updateProject(String ownerEmail, Long id, String name, String description) {
        Project project = getOwnProject(ownerEmail, id);
        project.changeDetails(normalizeText(name), normalizeDescription(description));
        validate(project);
        return projectRepository.saveAndFlush(project);
    }

    private void validate(Project project) {
        Set<ConstraintViolation<Project>> violations = validator.validate(project);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private String normalizeText(String value) {
        return value == null ? null : Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }

    private String normalizeDescription(String value) {
        String description = normalizeText(value);
        return description == null || description.isEmpty() ? null : description;
    }
}
