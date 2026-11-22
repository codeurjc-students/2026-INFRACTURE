package es.codeurjc.infracture.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// One thing that happened during an execution (#92). Its id orders the events and is the cursor of a
// reconnecting observer. It is never changed once written.
@Entity
@Immutable
@Table(name = "execution_events")
public class ExecutionEvent {

    public enum Kind {
        // A node changed state; written only when it changes.
        STATE,
        // A lifecycle step of the execution or a node, such as a phase change, a
        // failure or a response.
        EVENT,
        // One summarized line a node or the load printed.
        LOG
    }

    // The lifecycle steps an EVENT records, in its payload's "type".
    public static final String PHASE = "PHASE";
    public static final String STOP = "STOP";
    public static final String CLEANUP_FAILED = "CLEANUP_FAILED";
    public static final String RESPONSE = "RESPONSE";
    public static final String LOGS_OMITTED = "LOGS_OMITTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, updatable = false)
    private Long executionId;

    @Column(name = "component_key", updatable = false)
    private UUID componentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;

    @Column(nullable = false, updatable = false)
    private Instant at;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    protected ExecutionEvent() {
    }

    private ExecutionEvent(Long executionId, UUID componentKey, Kind kind, Instant at, Map<String, Object> payload) {
        this.executionId = executionId;
        this.componentKey = componentKey;
        this.kind = kind;
        this.at = at;
        this.payload = payload;
    }

    public static ExecutionEvent state(Long executionId, UUID componentKey, String status, Instant at) {
        return new ExecutionEvent(executionId, componentKey, Kind.STATE, at, Map.of("status", status));
    }

    public static ExecutionEvent event(Long executionId, UUID componentKey, String type, Map<String, Object> details,
            Instant at) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.putAll(details);
        return new ExecutionEvent(executionId, componentKey, Kind.EVENT, at, payload);
    }

    public static ExecutionEvent log(Long executionId, UUID componentKey, String line, Instant at) {
        return new ExecutionEvent(executionId, componentKey, Kind.LOG, at, Map.of("line", line));
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public UUID getComponentKey() {
        return componentKey;
    }

    public Kind getKind() {
        return kind;
    }

    public Instant getAt() {
        return at;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public String getType() {
        return kind == Kind.EVENT ? (String) payload.get("type") : null;
    }
}
