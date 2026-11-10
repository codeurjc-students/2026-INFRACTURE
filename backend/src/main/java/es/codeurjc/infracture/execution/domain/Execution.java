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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// One run of an exact scenario revision. The phase says what the engine is doing; the stop cause says why
// it stops. Stop, failure and interruption only record a cause; the engine then cleans up and releases.
// A failed cleanup is tracked apart from the cause, so it never changes how the execution ended.
@Entity
@Table(name = "executions")
public class Execution {

    // After this many failed cleanups in a row, the engine waits until the owner presses Stop again.
    public static final int AUTOMATIC_CLEANUP_ATTEMPTS = 3;
    private static final int MAX_REASON_LENGTH = 1000;

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

    // At most one: the frozen load, or none when the scenario had no load. A lazy collection keeps the load
    // out of the row-locking queries, which cannot join it.
    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL)
    private List<ExecutedWorkload> workload = new ArrayList<>();

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

    @Column(name = "cleanup_failures", nullable = false)
    private int cleanupFailures;

    @Column(name = "cleanup_error")
    private String cleanupError;

    @Column(name = "cleanup_failed_at")
    private Instant cleanupFailedAt;

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
        scenario.getWorkload().ifPresent(load -> workload.add(new ExecutedWorkload(this, load)));
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

    public void recordLoadSummary(LoadSummary summary, boolean completed) {
        ExecutedWorkload load = getWorkload();
        if (load == null) {
            throw new IllegalStateException("Execution %d has no load".formatted(id));
        }
        load.recordSummary(summary, completed);
    }

    public void startStarting() {
        require(Phase.RESERVED);
        phase = Phase.STARTING;
    }

    public void markRunning() {
        require(Phase.STARTING);
        phase = Phase.RUNNING;
    }

    // Stop during a failed cleanup asks for a new round of attempts at once. The last error stays
    // visible until one of them succeeds.
    public void requestStop() {
        recordCause(StopCause.STOP_REQUESTED);
        if (phase == Phase.STOPPING) {
            cleanupFailures = 0;
        }
    }

    public void fail(String reason) {
        if (isActive() && failureReason == null) {
            failureReason = shorten(reason);
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

    public void recordCleanupFailure(String reason) {
        require(Phase.STOPPING);
        cleanupFailures++;
        cleanupError = shorten(reason);
        cleanupFailedAt = Instant.now();
    }

    // The engine retries a failed cleanup on its own, each time after a longer pause, and then stops trying
    // until the owner asks again.
    public boolean isCleanupDue(Instant now, Duration pause) {
        if (cleanupFailures == 0) {
            return true;
        }
        if (isCleanupRetryNeeded()) {
            return false;
        }
        return !now.isBefore(cleanupFailedAt.plus(pause.multipliedBy(cleanupFailures)));
    }

    public boolean isCleanupRetryNeeded() {
        return cleanupFailures >= AUTOMATIC_CLEANUP_ATTEMPTS;
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

    private static String shorten(String reason) {
        return reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason;
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

    // Null when the scenario had no load.
    public ExecutedWorkload getWorkload() {
        return workload.isEmpty() ? null : workload.getFirst();
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

    public int getCleanupFailures() {
        return cleanupFailures;
    }

    public String getCleanupError() {
        return cleanupError;
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
