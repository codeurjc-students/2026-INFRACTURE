package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.application.ExecutionObservations;
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
    private final ExecutionObservations observations;

    public ExecutionController(ExecutionService service, ExecutionMapper mapper,
            ExecutionObservations observations) {
        this.service = service;
        this.mapper = mapper;
        this.observations = observations;
    }

    // 202: the request is accepted and reserved; starting the containers happens afterwards.
    @PostMapping
    public ResponseEntity<ExecutionDTO> run(
            @PathVariable Long projectId,
            @PathVariable Long scenarioId,
            @Valid @RequestBody RunExecutionDTO runDTO,
            Principal principal) {
        return ResponseEntity.accepted().body(mapper.toDTO(
                service.run(principal.getName(), projectId, scenarioId, runDTO.revision()), observations));
    }

    // 202: Stop is recorded; the cleanup runs afterwards and the phase shows when it ends.
    @PostMapping("/{executionId}/stop")
    public ResponseEntity<ExecutionDTO> stop(
            @PathVariable Long projectId,
            @PathVariable Long scenarioId,
            @PathVariable Long executionId,
            Principal principal) {
        return ResponseEntity.accepted().body(mapper.toDTO(
                service.stop(principal.getName(), projectId, scenarioId, executionId), observations));
    }

    @GetMapping("/current")
    public ResponseEntity<ExecutionDTO> getCurrent(
            @PathVariable Long projectId,
            @PathVariable Long scenarioId,
            Principal principal) {
        return service.getCurrent(principal.getName(), projectId, scenarioId)
                .map(execution -> ResponseEntity.ok(mapper.toDTO(execution, observations)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
