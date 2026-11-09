package es.codeurjc.infracture.execution.domain;

import es.codeurjc.infracture.project.domain.Workload;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// A frozen copy of the scenario load as it was when the execution was reserved.
@Entity
@Table(name = "executed_workloads")
public class ExecutedWorkload {

    @Id
    private Long id;

    @MapsId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id")
    private Execution execution;

    @Column(name = "target_key", nullable = false, updatable = false)
    private UUID targetKey;

    @Column(nullable = false, updatable = false)
    private String profile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Integer> parameters;

    @Column(nullable = false, updatable = false)
    private int seed;

    // Null until the load ends and its summary is read.
    @Column(name = "summary_collected_at")
    private Instant summaryCollectedAt;

    // Whether k6 ran its whole duration; false when Stop or a failure cut it short.
    private Boolean completed;

    @Embedded
    private LoadSummary summary;

    protected ExecutedWorkload() {
    }

    ExecutedWorkload(Execution execution, Workload workload) {
        this.execution = execution;
        this.targetKey = workload.targetKey();
        this.profile = workload.profile();
        this.parameters = Map.copyOf(workload.parameters());
        this.seed = workload.seed();
    }

    // The first summary wins: a later cleanup may read the same k6 output again.
    void recordSummary(LoadSummary summary, boolean completed) {
        if (summaryCollectedAt == null) {
            this.summaryCollectedAt = Instant.now();
            this.completed = completed;
            this.summary = summary;
        }
    }

    public boolean hasSummary() {
        return summaryCollectedAt != null;
    }

    public Instant getSummaryCollectedAt() {
        return summaryCollectedAt;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public LoadSummary getSummary() {
        return summary;
    }

    public UUID getTargetKey() {
        return targetKey;
    }

    public String getProfile() {
        return profile;
    }

    public Map<String, Integer> getParameters() {
        return parameters;
    }

    public int getSeed() {
        return seed;
    }
}
