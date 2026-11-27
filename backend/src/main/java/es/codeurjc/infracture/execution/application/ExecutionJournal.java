package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.application.ExecutionView.Node;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import es.codeurjc.infracture.execution.domain.ExecutionEvent.Kind;
import es.codeurjc.infracture.execution.domain.Phase;
import es.codeurjc.infracture.execution.domain.StopCause;
import es.codeurjc.infracture.execution.persistence.ExecutionEventRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

// Writes what happens during an execution as ExecutionEvents (#92), and reads back what was last seen of
// each node. Callers write inside the transaction of the change they describe, so an event never exists
// without its change, nor a change without its event.
@Component
public class ExecutionJournal {

    static final int MAX_LINES_PER_SOURCE = 1000;
    static final int MAX_LINES_PER_TICK = 100;

    private final ExecutionEventRepository events;
    private final LogRedactor redactor;

    public ExecutionJournal(ExecutionEventRepository events, LogRedactor redactor) {
        this.events = events;
        this.redactor = redactor;
    }

    // A photo of the three things of an execution that change: its phase, why
    // it stops and how many cleanups failed.
    public record Mark(Phase phase, StopCause cause, int cleanupFailures) {
    }

    public static Mark mark(Execution execution) {
        return new Mark(execution.getPhase(), execution.getStopCause(), execution.getCleanupFailures());
    }

    // Writes the first event of an execution, when Run reserves it
    public void recordReserved(Execution execution, Instant now) {
        events.save(ExecutionEvent.event(execution.getId(), null, ExecutionEvent.PHASE,
                Map.of("phase", execution.getPhase().name()), now));
    }

    // Onlny saved the execution changes not events.
    // Compares the photo taken before a change with the execution after it, and
    // writes one event per difference: STOP when it got a stop cause,
    // CLEANUP_FAILED when a cleanup failed again, PHASE when its phase moved.
    // The reason goes first, so the page shows why before what it caused.
    public void recordChanges(Mark before, Execution execution, Instant now) {
        Long id = execution.getId();
        if (execution.getStopCause() != before.cause()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("cause", execution.getStopCause().name());
            if (execution.getStopCause() == StopCause.FAILURE && execution.getFailureReason() != null) {
                details.put("reason", execution.getFailureReason());
            }
            events.save(ExecutionEvent.event(id, null, ExecutionEvent.STOP, details, now));
        }
        if (execution.getCleanupFailures() > before.cleanupFailures()) {
            events.save(ExecutionEvent.event(id, null, ExecutionEvent.CLEANUP_FAILED,
                    Map.of("error", execution.getCleanupError(), "attempt", execution.getCleanupFailures()), now));
        }
        if (execution.getPhase() != before.phase()) {
            events.save(ExecutionEvent.event(id, null, ExecutionEvent.PHASE,
                    Map.of("phase", execution.getPhase().name()), now));
        }
    }

    // Receives what Docker says of each node in this tick and writes a STATE
    // only for the nodes whose state changed since the last one stored. A node
    // healthy for ten minutes is one row, not one per tick.
    void recordStates(Long executionId, Map<UUID, ComponentStatus> statuses, Instant now) {
        Map<UUID, Node> seen = nodes(executionId);
        statuses.forEach((key, status) -> {
            Node node = seen.get(key);
            if (node == null || node.status() != status) {
                events.save(ExecutionEvent.state(executionId, key, status.name(), now));
            }
        });
    }

    // Writes what a node really answered when the backend called it, as
    // evidence that it works.
    void recordResponse(Long executionId, UUID componentKey, String response, Instant now) {
        events.save(ExecutionEvent.event(executionId, componentKey, ExecutionEvent.RESPONSE,
                Map.of("response", response), now));
    }

    // When the last stored log line was printed, or null when none was stored
    // yet. After a restart, the reconciler asks Docker for the lines after this
    // moment, so no line is stored twice.
    Instant lastLogAt(Long executionId) {
        return events.findFirstByExecutionIdAndKindOrderByAtDescIdDesc(executionId, Kind.LOG)
                .map(ExecutionEvent::getAt).orElse(null);
    }

    // Stores the log lines printed after since, and returns the time of the
    // last one it processed, so the next tick continues from there. Each line is
    // cleaned of secrets first. At most 100 lines per tick (the rest wait for the
    // next tick, except those printed at the same instant as the last one) and
    // 1000 per node (the rest are only counted in one LOGS_OMITTED). Lines of the
    // load have no component.
    Instant recordLogs(Execution execution, List<LogLine> lines, Instant since) {
        List<LogLine> fresh = lines.stream()
                .filter(line -> since == null || line.at().isAfter(since))
                .sorted(Comparator.comparing(LogLine::at))
                .toList();
        // A tick never stops between two lines printed at the same instant: the
        // next one reads only after that instant, so it would miss the rest.
        int end = Math.min(fresh.size(), MAX_LINES_PER_TICK);
        while (end > 0 && end < fresh.size() && fresh.get(end).at().equals(fresh.get(end - 1).at())) {
            end++;
        }
        List<LogLine> thisTick = fresh.subList(0, end);
        Map<UUID, Long> stored = new HashMap<>();
        Map<UUID, Integer> omitted = new LinkedHashMap<>();
        for (LogLine line : thisTick) {
            UUID key = line.node();
            long count = stored.computeIfAbsent(key, source -> count(execution.getId(), source));
            if (count < MAX_LINES_PER_SOURCE) {
                events.save(ExecutionEvent.log(execution.getId(), key, redactor.clean(line.text(), execution.getPlan()),
                        line.at()));
                stored.put(key, count + 1);
            } else {
                omitted.merge(key, 1, Integer::sum);
            }
        }
        Instant readUntil = thisTick.isEmpty() ? since : thisTick.getLast().at();
        omitted.forEach((key, count) -> events.save(ExecutionEvent.event(execution.getId(), key,
                ExecutionEvent.LOGS_OMITTED, Map.of("count", count), readUntil)));
        return readUntil;
    }

    // Replays the stored events in order and keeps what was last seen of each
    // node: its state and its real response. The canvas shows this, and
    // recordStates compares against it.
    public Map<UUID, Node> nodes(Long executionId) {
        Map<UUID, Node> nodes = new HashMap<>();
        for (ExecutionEvent event : events.findByExecutionIdAndKindNotOrderById(executionId, Kind.LOG)) {
            UUID key = event.getComponentKey();
            if (key == null) {
                continue;
            }
            Node old = nodes.getOrDefault(key, Node.NOTHING_SEEN);
            if (event.getKind() == Kind.STATE) {
                nodes.put(key, new Node(ComponentStatus.valueOf((String) event.getPayload().get("status")),
                        old.response()));
            } else if (ExecutionEvent.RESPONSE.equals(event.getType())) {
                nodes.put(key, new Node(old.status(), (String) event.getPayload().get("response")));
            }
        }
        return nodes;
    }

    // How many log lines a node, or the load when componentKey is null, already
    // has stored.
    private long count(Long executionId, UUID componentKey) {
        return componentKey == null
                ? events.countByExecutionIdAndComponentKeyIsNullAndKind(executionId, Kind.LOG)
                : events.countByExecutionIdAndComponentKeyAndKind(executionId, componentKey, Kind.LOG);
    }
}
