package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.application.ExecutionMetricsService;
import java.security.Principal;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/scenarios/{scenarioId}/executions/{executionId}")
public class ExecutionMetricsController {

    private final ExecutionMetricsService service;

    public ExecutionMetricsController(ExecutionMetricsService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public ExecutionMetricsDTO getMetrics(
            @PathVariable Long projectId,
            @PathVariable Long scenarioId,
            @PathVariable Long executionId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "" + ExecutionMetricsService.MAX_SAMPLES) int limit,
            Principal principal) {
        return ExecutionMetricsDTO.of(
                service.read(principal.getName(), projectId, scenarioId, executionId, from, to, after, limit));
    }
}
