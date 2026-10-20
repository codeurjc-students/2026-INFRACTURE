package es.codeurjc.infracture.project.api;

import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

import es.codeurjc.infracture.project.application.ProjectService;
import es.codeurjc.infracture.project.domain.Project;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService service;
    private final ProjectMapper mapper;

    public ProjectController(ProjectService service, ProjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ProjectDTO> createProject(@RequestBody SaveProjectDTO projectDTO, Principal principal) {
        Project project = service.createProject(principal.getName(), projectDTO.name(), projectDTO.description());
        URI location = fromCurrentRequest().path("/{id}").buildAndExpand(project.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toDTO(project));
    }

    @GetMapping
    public Page<ProjectDTO> getOwnProjects(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            Principal principal) {
        return service
                .getOwnProjects(principal.getName(), PageRequest.of(page, size))
                .map(mapper::toDTO);
    }

    @GetMapping("/{id}")
    public ProjectDTO getOwnProject(@PathVariable Long id, Principal principal) {
        return mapper.toDTO(service.getOwnProject(principal.getName(), id));
    }

    @PutMapping("/{id}")
    public ProjectDTO updateProject(
            @PathVariable Long id,
            @RequestBody SaveProjectDTO projectDTO,
            Principal principal) {
        Project project = service.updateProject(principal.getName(), id, projectDTO.name(), projectDTO.description());
        return mapper.toDTO(project);
    }

}
