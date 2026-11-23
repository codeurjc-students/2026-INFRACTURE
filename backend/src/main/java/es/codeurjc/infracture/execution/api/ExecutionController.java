package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.application.ExecutionService;
import es.codeurjc.infracture.execution.application.ExecutionView;
import es.codeurjc.infracture.security.AuthenticatedUser;
import es.codeurjc.infracture.security.jwt.JwtRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/scenarios/{scenarioId}/executions")
public class ExecutionController {

    private final ExecutionService service;
    private final ExecutionMapper mapper;
    private final ExecutionEventStream stream;

    public ExecutionController(ExecutionService service, ExecutionMapper mapper, ExecutionEventStream stream) {
        this.service = service;
        this.mapper = mapper;
        this.stream = stream;
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

    // 202: Stop is recorded; the cleanup runs afterwards and the phase shows when it ends.
    @PostMapping("/{executionId}/stop")
    public ResponseEntity<ExecutionDTO> stop(
            @PathVariable Long projectId,
            @PathVariable Long scenarioId,
            @PathVariable Long executionId,
            Principal principal) {
        return ResponseEntity.accepted().body(mapper.toDTO(
                service.stop(principal.getName(), projectId, scenarioId, executionId)));
    }

    @GetMapping("/current")
    public ResponseEntity<ExecutionDTO> getCurrent(
            @PathVariable Long projectId,
            @PathVariable Long scenarioId,
            Principal principal) {
        return service.getCurrent(principal.getName(), projectId, scenarioId)
                .map(view -> ResponseEntity.ok(mapper.toDTO(view)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Server-Sent Events with what happens in the execution (#92). The browser resumes after a cut with
    // Last-Event-ID; a new stream opened after renewing the session passes the same cursor as ?after.
    @GetMapping(path = "/{executionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable Long projectId,
            @PathVariable Long scenarioId,
            @PathVariable Long executionId,
            @RequestHeader(name = "Last-Event-ID", required = false) Long lastEventId,
            @RequestParam(required = false) Long after,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        ExecutionView view = service.getOwn(user.getUsername(), projectId, scenarioId, executionId);
        Instant tokenExpiresAt = (Instant) request.getAttribute(JwtRequestFilter.TOKEN_EXPIRES_AT);
        return stream.open(user.getId(), user.getTokenVersion(), view, lastEventId != null ? lastEventId : after,
                tokenExpiresAt);
    }
}
