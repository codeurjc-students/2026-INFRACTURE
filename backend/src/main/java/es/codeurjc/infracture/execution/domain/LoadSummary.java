package es.codeurjc.infracture.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// What k6 reported for the whole load, aggregated: never one row per request. A value k6 did not report
// stays null instead of becoming 0.
@Embeddable
public record LoadSummary(
        Long requests,
        @Column(name = "failed_requests") Long failedRequests,
        @Column(name = "dropped_iterations") Long droppedIterations,
        @Column(name = "average_latency_ms") Double averageLatencyMs,
        @Column(name = "p95_latency_ms") Double p95LatencyMs) {
}
