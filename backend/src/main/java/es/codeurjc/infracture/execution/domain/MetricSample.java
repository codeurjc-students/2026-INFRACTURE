package es.codeurjc.infracture.execution.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Immutable;

// What Docker and the node's own counters reported for one executed node at one moment. A null value or an
// absent counter means there was no reading.
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

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "execution_counter_samples", joinColumns = @JoinColumn(name = "metric_sample_id"))
    @MapKeyColumn(name = "name", length = 100)
    @Column(name = "value", nullable = false)
    private Map<String, Double> counters = new LinkedHashMap<>();

    protected MetricSample() {
    }

    public MetricSample(Long executedComponentId, Instant sampledAt, Double cpuPercent, Long memoryBytes,
            Map<String, Double> counters) {
        this.executedComponentId = executedComponentId;
        this.sampledAt = sampledAt;
        this.cpuPercent = cpuPercent;
        this.memoryBytes = memoryBytes;
        this.counters = counters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(counters);
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

    public Map<String, Double> getCounters() {
        return Collections.unmodifiableMap(counters);
    }
}
