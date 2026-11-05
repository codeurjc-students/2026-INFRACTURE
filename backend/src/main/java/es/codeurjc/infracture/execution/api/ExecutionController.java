package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.application.ExecutionService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/scenarios/{scenarioId}/executions")
public class ExecutionController {

    private final ExecutionService service;
    private final ExecutionMapper mapper;

    public ExecutionController(ExecutionService service, ExecutionMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    // 202: the request is accepted and reserved; starting the containers happens afterwards.
    @PostMapping
    public ResponseEntity<ExecutionDTO> run(
            @PathVariable Long projectId,
            @PathVariable Long scenarioId,
            @Valid @RequestBody RunExecutionDTO runDTO,
            Principal principal) {
        return ResponseEntity.accepted().body(mapper.toDTO(
                service.run(principal.getName(), projectId, scenarioId, runDTO.revision())));
    }

    @GetMapping("/current")
    public ResponseEntity<ExecutionDTO> getCurrent(
            @PathVariable Long projectId,
            @PathVariable Long scenarioId,
            Principal principal) {
        return service.getCurrent(principal.getName(), projectId, scenarioId)
                .map(execution -> ResponseEntity.ok(mapper.toDTO(execution)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
