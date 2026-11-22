package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.application.ExecutionEventSignal;
import es.codeurjc.infracture.execution.application.ExecutionFeed;
import es.codeurjc.infracture.execution.application.ExecutionFeed.Batch;
import es.codeurjc.infracture.execution.application.ExecutionView;
import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Sends one execution's events to one browser tab (#92, ADR 0017). Each stream runs on its own virtual
// thread, so a slow or closed tab never holds up the reconciler or other tabs. It reads the database when
// the ExecutionEventSignal says the execution has new events, and at the latest once per heartbeat; while
// the signal is not listening, it reads once per poll interval instead.
// The stream ends when the access token it was opened with expires (after a "renew" message, so the page
// renews the session and reopens from the same cursor), when the account stops being active, when the
// execution is released and everything was sent, or when the tab goes away.
@Component
public class ExecutionEventStream {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEventStream.class);

    // shortcut: counted per backend process; with several backends each one applies it on its own.
    static final int MAX_STREAMS_PER_USER = 5;
    // Matches the page the feed reads; a full page means more are waiting, so the next read does not sleep.
    static final int PAGE_SIZE = 200;
    // Extra time after the token expiry, so the stream itself sends "renew" before the server times it out.
    private static final Duration TIMEOUT_MARGIN = Duration.ofSeconds(30);

    private final ExecutionFeed feed;
    private final ExecutionEventSignal signal;
    private final ExecutionMapper mapper;
    private final Clock clock;
    private final Duration pollInterval;
    private final Duration heartbeat;
    private final Map<String, Integer> openByUser = new ConcurrentHashMap<>();
    private volatile boolean closing;

    public ExecutionEventStream(ExecutionFeed feed, ExecutionEventSignal signal, ExecutionMapper mapper, Clock clock,
            @Value("${infracture.execution.events.poll-interval:1s}") Duration pollInterval,
            @Value("${infracture.execution.events.heartbeat:15s}") Duration heartbeat) {
        this.feed = feed;
        this.signal = signal;
        this.mapper = mapper;
        this.clock = clock;
        this.pollInterval = pollInterval;
        this.heartbeat = heartbeat;
    }

    public SseEmitter open(String email, ExecutionView view, Long cursor, Instant tokenExpiresAt) {
        if (openByUser.merge(email, 1, Integer::sum) > MAX_STREAMS_PER_USER) {
            release(email);
            throw new TooManyStreamsException(MAX_STREAMS_PER_USER);
        }
        Instant expiresAt = tokenExpiresAt == null ? clock.instant() : tokenExpiresAt;
        SseEmitter emitter = new SseEmitter(
                Math.max(1, Duration.between(clock.instant(), expiresAt).plus(TIMEOUT_MARGIN).toMillis()));
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        Thread.ofVirtual().name("execution-events-" + view.execution().getId()).start(() -> {
            try {
                send(emitter, email, view, cursor, expiresAt, closed::get);
            } finally {
                release(email);
            }
        });
        return emitter;
    }

    // Runs until the stream ends. Package-visible so tests can drive it on their own thread.
    void send(SseEmitter emitter, String email, ExecutionView view, Long cursor, Instant expiresAt,
            BooleanSupplier closed) {
        Long executionId = view.execution().getId();
        signal.watch(executionId);
        try {
            long after = cursor == null ? 0 : cursor;
            // A cursor the database no longer has (purged, or of another execution) cannot be resumed:
            // the page gets the whole current state and continues from the newest event.
            if (after > 0 && !feed.isKnown(executionId, after)) {
                emitter.send(SseEmitter.event().name("reset").data(mapper.toDTO(view), MediaType.APPLICATION_JSON));
                after = feed.latest(executionId);
            }
            Instant lastSent = clock.instant();
            while (!closed.getAsBoolean() && !closing) {
                Instant now = clock.instant();
                if (!now.isBefore(expiresAt)) {
                    emitter.send(SseEmitter.event().name("renew").data(Map.of("after", after),
                            MediaType.APPLICATION_JSON));
                    break;
                }
                long seen = signal.version(executionId);
                Batch batch = feed.next(email, executionId, after);
                if (!batch.allowed()) {
                    break;
                }
                for (ExecutionEvent event : batch.events()) {
                    emitter.send(message(event));
                    after = event.getId();
                }
                if (!batch.events().isEmpty()) {
                    lastSent = now;
                } else if (batch.released()) {
                    break;
                } else if (!Duration.between(lastSent, now).minus(heartbeat).isNegative()) {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                    lastSent = now;
                }
                // A released execution has nothing more to wait for: the next read ends the stream.
                if (!batch.released() && batch.events().size() < PAGE_SIZE) {
                    Duration wait = signal.isListening() ? heartbeat : pollInterval;
                    Duration untilExpiry = Duration.between(now, expiresAt);
                    signal.await(executionId, seen, wait.compareTo(untilExpiry) < 0 ? wait : untilExpiry);
                }
            }
            emitter.complete();
        } catch (IOException | IllegalStateException _) {
            // The tab went away or the server already ended the response: nothing is left to send to.
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            // The database could not answer. Ending the response makes the browser reconnect on its own and
            // resume after the last event it received.
            log.warn("Event stream of execution {} ended early", executionId, exception);
            emitter.complete();
        } finally {
            signal.unwatch(executionId);
        }
    }

    // A shutdown waits for open requests, and a stream would stay open until its token expires. Ending the
    // streams first lets the backend stop at once; each browser reconnects and resumes from its cursor.
    @EventListener(ContextClosedEvent.class)
    void close() {
        closing = true;
        signal.wakeEveryStream();
    }

    // id: the event's cursor. event: state, event or log. data: the event with its payload fields.
    private static SseEmitter.SseEventBuilder message(ExecutionEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", event.getId());
        data.put("executionId", event.getExecutionId());
        data.put("componentKey", event.getComponentKey());
        data.put("kind", event.getKind().name());
        data.put("at", event.getAt());
        data.putAll(event.getPayload());
        return SseEmitter.event().id(String.valueOf(event.getId()))
                .name(event.getKind().name().toLowerCase(Locale.ROOT))
                .data(data, MediaType.APPLICATION_JSON);
    }

    private void release(String email) {
        openByUser.computeIfPresent(email, (user, count) -> count > 1 ? count - 1 : null);
    }
}
