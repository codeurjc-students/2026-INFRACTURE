package es.codeurjc.infracture.execution.domain;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.project.domain.Component;
import es.codeurjc.infracture.project.domain.Connection;
import es.codeurjc.infracture.project.domain.Scenario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// One run of an exact scenario revision. The phase says what the engine is doing; the stop cause says why
// it stops. Stop, failure and interruption only record a cause; the engine then cleans up and releases.
@Entity
@Table(name = "executions")
public class Execution {

    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false, updatable = false)
    private Scenario scenario;

    @Column(name = "scenario_revision", nullable = false, updatable = false)
    private int scenarioRevision;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ExecutedComponent> components = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "executed_connections", joinColumns = @JoinColumn(name = "execution_id"))
    private List<Connection> connections = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Phase phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_cause")
    private StopCause stopCause;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    @Column(name = "failure_reason")
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> plan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected Execution() {
    }

    public Execution(Scenario scenario, Map<ComponentType, Integer> contractVersions) {
        this.scenario = scenario;
        this.scenarioRevision = scenario.getRevision();
        for (Component component : scenario.getComponents()) {
            components.add(new ExecutedComponent(this, component, contractVersions.get(component.getType())));
        }
        this.connections.addAll(scenario.getConnections());
        this.phase = Phase.RESERVED;
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        this.updatedAt = Instant.now();
    }

    // The plan names resources after the execution id, so it is attached once the reservation has an id.
    public void attachPlan(Map<String, Object> plan) {
        if (this.plan != null) {
            throw new IllegalStateException("Execution %d already has a plan".formatted(id));
        }
        this.plan = plan;
    }

    public void startStarting() {
        require(Phase.RESERVED);
        phase = Phase.STARTING;
    }

    public void markRunning() {
        require(Phase.STARTING);
        phase = Phase.RUNNING;
    }

    public void requestStop() {
        recordCause(StopCause.STOP_REQUESTED);
    }

    public void fail(String reason) {
        if (isActive() && failureReason == null) {
            failureReason = reason.length() > MAX_FAILURE_REASON_LENGTH
                    ? reason.substring(0, MAX_FAILURE_REASON_LENGTH)
                    : reason;
        }
        recordCause(StopCause.FAILURE);
    }

    public void interrupt() {
        recordCause(StopCause.INTERRUPTION);
    }

    public void beginStopping() {
        if (!isActive() || stopCause == null) {
            throw new IllegalStateException("Execution %d has nothing to stop in phase %s".formatted(id, phase));
        }
        phase = Phase.STOPPING;
    }

    public void release() {
        require(Phase.STOPPING);
        phase = Phase.RELEASED;
        outcome = stopCause.outcome();
        releasedAt = Instant.now();
    }

    public boolean isActive() {
        return phase != Phase.RELEASED;
    }

    private void recordCause(StopCause cause) {
        if (isActive() && cause.isWorseThan(stopCause)) {
            stopCause = cause;
        }
    }

    private void require(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "Execution %d must be %s, but it is %s".formatted(id, expected, phase));
        }
    }

    public Long getId() {
        return id;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public int getScenarioRevision() {
        return scenarioRevision;
    }

    public List<ExecutedComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }

    public List<Connection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public Phase getPhase() {
        return phase;
    }

    public StopCause getStopCause() {
        return stopCause;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Map<String, Object> getPlan() {
        return plan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
