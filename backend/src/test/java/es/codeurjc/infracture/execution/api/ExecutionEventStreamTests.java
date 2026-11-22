package es.codeurjc.infracture.execution.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.application.ExecutionEventSignal;
import es.codeurjc.infracture.execution.application.ExecutionFeed;
import es.codeurjc.infracture.execution.application.ExecutionFeed.Batch;
import es.codeurjc.infracture.execution.application.ExecutionView;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.security.MutableClock;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ExecutionEventStreamTests {

    private static final String OWNER = "alice@example.com";
    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final Instant TOKEN_EXPIRES = NOW.plusSeconds(300);

    private final ExecutionFeed feed = mock(ExecutionFeed.class);
    private final MutableClock clock = new MutableClock(NOW);
    // Not started, so it never listens: the stream reads again after each poll interval, here at once.
    private final ExecutionEventSignal signal = new ExecutionEventSignal(null);
    private final ExecutionEventStream stream = new ExecutionEventStream(feed, signal, new ExecutionMapperImpl(),
            clock, Duration.ZERO, Duration.ofSeconds(15));
    private final RecordingEmitter emitter = new RecordingEmitter();
    private ExecutionView view;

    @BeforeEach
    void reserve() {
        ComponentTemplate http = new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE,
                true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)));
        Scenario scenario = new Scenario(new Project(
                new User("alice", "hash", OWNER, "Alice", UserRole.USER), "Lab", null), "Run", null);
        scenario.replaceGraph(1, List.of(new ComponentDraft(ORDERS, "http-service", "orders-api", 0, 0,
                new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)))), List.of(), null,
                Map.of("http-service", http));
        Execution execution = new Execution(scenario, Map.of(ComponentType.HTTP_SERVICE, 1));
        ReflectionTestUtils.setField(execution, "id", 12L);
        view = new ExecutionView(execution, Map.of(), null);
    }

    @Test
    void sendsTheEventsInOrderAndEndsOnceTheExecutionIsReleased() {
        when(feed.next(OWNER, 12L, 0)).thenReturn(new Batch(List.of(
                event(1, ExecutionEvent.event(12L, null, "PHASE", Map.of("phase", "STARTING"), NOW)),
                event(2, ExecutionEvent.state(12L, ORDERS, "HEALTHY", NOW))), false, true));
        when(feed.next(OWNER, 12L, 2)).thenReturn(new Batch(List.of(), true, true));

        stream.send(emitter, OWNER, view, null, TOKEN_EXPIRES, () -> false);

        assertThat(emitter.messages).containsExactly(
                "id:1|event:event|{id=1, executionId=12, componentKey=null, kind=EVENT, at=" + NOW
                        + ", type=PHASE, phase=STARTING}",
                "id:2|event:state|{id=2, executionId=12, componentKey=" + ORDERS + ", kind=STATE, at=" + NOW
                        + ", status=HEALTHY}");
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void endsRightAfterTheLastEventsOfAReleasedExecutionWithoutWaiting() {
        ExecutionEventStream slow = new ExecutionEventStream(feed, signal, new ExecutionMapperImpl(), clock,
                Duration.ofHours(1), Duration.ofHours(1));
        when(feed.next(OWNER, 12L, 0)).thenReturn(new Batch(List.of(
                event(3, ExecutionEvent.event(12L, null, "PHASE", Map.of("phase", "RELEASED"), NOW))), true, true));
        when(feed.next(OWNER, 12L, 3)).thenReturn(new Batch(List.of(), true, true));

        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> slow.send(emitter, OWNER, view, null, TOKEN_EXPIRES, () -> false));

        assertThat(emitter.messages).hasSize(1);
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void endsAWaitingStreamWhenTheBackendShutsDown() throws InterruptedException {
        ExecutionEventStream slow = new ExecutionEventStream(feed, signal, new ExecutionMapperImpl(), clock,
                Duration.ofHours(1), Duration.ofHours(1));
        when(feed.next(OWNER, 12L, 0)).thenReturn(new Batch(List.of(), false, true));
        Thread sending = Thread.ofVirtual().start(
                () -> slow.send(emitter, OWNER, view, null, TOKEN_EXPIRES, () -> false));
        verify(feed, timeout(5000)).next(OWNER, 12L, 0);

        slow.close();

        assertThat(sending.join(Duration.ofSeconds(5))).isTrue();
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void resumesAfterTheCursorItWasGiven() {
        when(feed.isKnown(12L, 7)).thenReturn(true);
        when(feed.next(OWNER, 12L, 7)).thenReturn(new Batch(List.of(), true, true));

        stream.send(emitter, OWNER, view, 7L, TOKEN_EXPIRES, () -> false);

        assertThat(emitter.messages).isEmpty();
        verify(feed, never()).next(OWNER, 12L, 0);
    }

    @Test
    void sendsTheWholeStateWhenTheCursorIsGoneAndContinuesFromTheNewestEvent() {
        when(feed.isKnown(12L, 99)).thenReturn(false);
        when(feed.latest(12L)).thenReturn(40L);
        when(feed.next(OWNER, 12L, 40)).thenReturn(new Batch(List.of(), true, true));

        stream.send(emitter, OWNER, view, 99L, TOKEN_EXPIRES, () -> false);

        assertThat(emitter.messages).singleElement().asString()
                .startsWith("event:reset|ExecutionDTO[id=12, scenarioId=null, scenarioRevision=2, phase=RESERVED");
    }

    @Test
    void asksThePageToRenewTheSessionWhenTheTokenExpires() {
        when(feed.isKnown(12L, 5)).thenReturn(true);
        clock.advance(Duration.ofSeconds(300));

        stream.send(emitter, OWNER, view, 5L, TOKEN_EXPIRES, () -> false);

        assertThat(emitter.messages).containsExactly("event:renew|{after=5}");
        assertThat(emitter.completed).isTrue();
        verify(feed, never()).next(anyString(), anyLong(), anyLong());
    }

    @Test
    void endsWhenTheAccountIsNoLongerActive() {
        when(feed.next(OWNER, 12L, 0)).thenReturn(new Batch(List.of(), false, false));

        stream.send(emitter, OWNER, view, null, TOKEN_EXPIRES, () -> false);

        assertThat(emitter.messages).isEmpty();
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void keepsAQuietStreamAliveWithAComment() {
        when(feed.next(OWNER, 12L, 0))
                .thenAnswer(invocation -> {
                    clock.advance(Duration.ofSeconds(16));
                    return new Batch(List.of(), false, true);
                })
                .thenReturn(new Batch(List.of(), false, true))
                .thenReturn(new Batch(List.of(), true, true));

        stream.send(emitter, OWNER, view, null, TOKEN_EXPIRES, () -> false);

        assertThat(emitter.messages).containsExactly(":keep-alive");
    }

    @Test
    void stopsSendingWhenTheTabGoesAway() {
        when(feed.next(eq(OWNER), eq(12L), anyLong()))
                .thenReturn(new Batch(List.of(event(1, ExecutionEvent.state(12L, ORDERS, "HEALTHY", NOW))),
                        false, true));
        emitter.failure = new IOException("Broken pipe");

        stream.send(emitter, OWNER, view, null, TOKEN_EXPIRES, () -> false);

        assertThat(emitter.completed).isFalse();
    }

    @Test
    void endsTheStreamWhenTheDatabaseCannotAnswerSoTheBrowserReconnects() {
        when(feed.next(OWNER, 12L, 0)).thenThrow(new QueryTimeoutException("statement timeout"));

        stream.send(emitter, OWNER, view, null, TOKEN_EXPIRES, () -> false);

        assertThat(emitter.messages).isEmpty();
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void refusesASixthStreamOfTheSameAccount() {
        when(feed.next(anyString(), eq(12L), anyLong())).thenReturn(new Batch(List.of(), false, true));
        ExecutionEventStream slow =
                new ExecutionEventStream(feed, signal, new ExecutionMapperImpl(), clock, Duration.ofSeconds(1),
                        Duration.ofSeconds(15));
        List<SseEmitter> open = new ArrayList<>();
        for (int tab = 0; tab < ExecutionEventStream.MAX_STREAMS_PER_USER; tab++) {
            open.add(slow.open(OWNER, view, null, TOKEN_EXPIRES));
        }

        assertThatThrownBy(() -> slow.open(OWNER, view, null, TOKEN_EXPIRES))
                .isInstanceOf(TooManyStreamsException.class);
        assertThat(slow.open("bob@example.com", view, null, TOKEN_EXPIRES)).isNotNull();
        open.forEach(SseEmitter::complete);
    }

    private static ExecutionEvent event(long id, ExecutionEvent event) {
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    // Keeps each message as one line: the SSE fields it sets, then its data, joined by "|".
    private static final class RecordingEmitter extends SseEmitter {

        private final List<String> messages = new ArrayList<>();
        private boolean completed;
        private IOException failure;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failure != null) {
                throw failure;
            }
            StringBuilder raw = new StringBuilder();
            for (DataWithMediaType part : builder.build()) {
                raw.append(part.getData());
            }
            List<String> lines = new ArrayList<>();
            for (String line : raw.toString().replace("data:", "").split("\n")) {
                if (!line.isBlank()) {
                    lines.add(line.strip());
                }
            }
            messages.add(String.join("|", lines));
        }

        @Override
        public void complete() {
            completed = true;
        }
    }
}
