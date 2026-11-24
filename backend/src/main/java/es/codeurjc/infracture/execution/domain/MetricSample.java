package es.codeurjc.infracture.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

// What Docker reported for one executed node at one moment. A null value means there was no reading.
@Entity
@Immutable
@Table(name = "execution_metric_samples")
public class MetricSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "executed_component_id", nullable = false, updatable = false)
    private Long executedComponentId;

    @Column(name = "sampled_at", nullable = false, updatable = false)
    private Instant sampledAt;

    @Column(name = "cpu_percent", updatable = false)
    private Double cpuPercent;

    @Column(name = "memory_bytes", updatable = false)
    private Long memoryBytes;

    protected MetricSample() {
    }

    public MetricSample(Long executedComponentId, Instant sampledAt, Double cpuPercent, Long memoryBytes) {
        this.executedComponentId = executedComponentId;
        this.sampledAt = sampledAt;
        this.cpuPercent = cpuPercent;
        this.memoryBytes = memoryBytes;
    }

    public Long getId() {
        return id;
    }

    public Long getExecutedComponentId() {
        return executedComponentId;
    }

    public Instant getSampledAt() {
        return sampledAt;
    }

    public Double getCpuPercent() {
        return cpuPercent;
    }

    public Long getMemoryBytes() {
        return memoryBytes;
    }
}
