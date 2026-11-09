package es.codeurjc.infracture.execution.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// The load exactly as the execution froze it and, once it ended, what k6 reported. summaryCollectedAt and
// completed stay null until then; summary stays null when k6 reported nothing.
public record ExecutedWorkloadDTO(UUID targetKey, String profile, Map<String, Integer> parameters, int seed,
        Instant summaryCollectedAt, Boolean completed, LoadSummaryDTO summary) {

    // Each value is null when k6 did not report it.
    public record LoadSummaryDTO(Long requests, Long failedRequests, Long droppedIterations,
            Double averageLatencyMs, Double p95LatencyMs) {
    }
}
