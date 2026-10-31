package es.codeurjc.infracture.project.api;

import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

import es.codeurjc.infracture.project.application.ScenarioService;
import es.codeurjc.infracture.project.domain.Scenario;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/projects/{projectId}/scenarios")
public class ScenarioController {

    private final ScenarioService service;
    private final ScenarioMapper mapper;

    public ScenarioController(ScenarioService service, ScenarioMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ScenarioDTO> createScenario(
            @PathVariable Long projectId,
            @RequestBody SaveScenarioDTO scenarioDTO,
            Principal principal) {
        Scenario scenario = service.createScenario(
                principal.getName(), projectId, scenarioDTO.name(), scenarioDTO.description());
        URI location = fromCurrentRequest().path("/{id}").buildAndExpand(scenario.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toDTO(scenario));
    }

    @GetMapping
    public Page<ScenarioDTO> getScenarios(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            Principal principal) {
        return service
                .getScenarios(principal.getName(), projectId, PageRequest.of(page, size))
                .map(mapper::toDTO);
    }

    @GetMapping("/{id}")
    public ScenarioDTO getScenario(@PathVariable Long projectId, @PathVariable Long id, Principal principal) {
        return mapper.toDTO(service.getScenario(principal.getName(), projectId, id));
    }

    @PutMapping("/{id}")
    public ScenarioDTO updateScenario(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @RequestBody SaveScenarioDTO scenarioDTO,
            Principal principal) {
        Scenario scenario = service.updateScenario(
                principal.getName(), projectId, id, scenarioDTO.name(), scenarioDTO.description());
        return mapper.toDTO(scenario);
    }

    @GetMapping("/{id}/graph")
    public ScenarioGraphDTO getGraph(@PathVariable Long projectId, @PathVariable Long id, Principal principal) {
        return mapper.toDTO(service.getGraph(principal.getName(), projectId, id));
    }

    @PutMapping("/{id}/graph")
    public ScenarioGraphDTO saveGraph(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody SaveScenarioGraphDTO graphDTO,
            Principal principal) {
        return mapper.toDTO(service.saveGraph(principal.getName(), projectId, id, graphDTO.revision(),
                graphDTO.components().stream().map(mapper::toDraft).toList()));
    }
}
