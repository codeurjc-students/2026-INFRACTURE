package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import es.codeurjc.infracture.execution.domain.ExecutionEvent.Kind;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExecutionJournalTests {

    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final Instant START = Instant.parse("2026-11-21T10:00:00Z");

    private final InMemoryExecutionEvents events = new InMemoryExecutionEvents();
    private final ExecutionJournal journal = new ExecutionJournal(events.repository, new LogRedactor("", ""));
    private Execution execution;

    @BeforeEach
    void reserve() {
        ComponentTemplate http = new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE,
                true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)));
        Scenario scenario = new Scenario(new Project(
                new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER), "Lab", null), "Run", null);
        scenario.replaceGraph(1, List.of(new ComponentDraft(ORDERS, "http-service", "orders-api", 0, 0,
                new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)))), List.of(), null,
                Map.of("http-service", http));
        execution = new Execution(scenario, Map.of(ComponentType.HTTP_SERVICE, 1));
        ReflectionTestUtils.setField(execution, "id", 12L);
    }

    @Test
    void storesAtMostOneHundredLinesPerTickAndReadsTheRestNextTime() {
        List<LogLine> printed = lines(150);

        Instant firstTick = journal.recordLogs(execution, printed, null);
        Instant secondTick = journal.recordLogs(execution, printed, firstTick);

        assertThat(firstTick).isEqualTo(START.plusMillis(99));
        assertThat(secondTick).isEqualTo(START.plusMillis(149));
        assertThat(events.of(12L, Kind.LOG)).hasSize(150)
                .extracting(event -> event.getPayload().get("line")).doesNotHaveDuplicates();
    }

    @Test
    void keepsTheLinesPrintedAtTheSameInstantInTheSameTick() {
        List<LogLine> printed = new ArrayList<>(lines(ExecutionJournal.MAX_LINES_PER_TICK));
        Instant last = START.plusMillis(ExecutionJournal.MAX_LINES_PER_TICK - 1);
        printed.add(new LogLine(ORDERS, last, "same instant"));
        printed.add(new LogLine(ORDERS, last.plusMillis(1), "later"));

        Instant firstTick = journal.recordLogs(execution, printed, null);
        Instant secondTick = journal.recordLogs(execution, printed, firstTick);

        assertThat(firstTick).isEqualTo(last);
        assertThat(secondTick).isEqualTo(last.plusMillis(1));
        assertThat(events.of(12L, Kind.LOG)).hasSize(ExecutionJournal.MAX_LINES_PER_TICK + 2)
                .extracting(event -> event.getPayload().get("line")).contains("same instant", "later");
    }

    @Test
    void storesTheLinesOfTheLoadWithoutAComponent() {
        journal.recordLogs(execution, List.of(new LogLine(null, START, "k6 running")), null);

        assertThat(events.of(12L, Kind.LOG)).singleElement()
                .satisfies(event -> assertThat(event.getComponentKey()).isNull())
                .extracting(event -> event.getPayload().get("line")).isEqualTo("k6 running");
    }

    @Test
    void countsTheLinesANodePrintsPastItsLimitInsteadOfStoringThem() {
        Instant readUntil = null;
        List<LogLine> printed = lines(ExecutionJournal.MAX_LINES_PER_SOURCE + 30);
        for (int tick = 0; tick < 11; tick++) {
            readUntil = journal.recordLogs(execution, printed, readUntil);
        }

        assertThat(events.of(12L, Kind.LOG)).hasSize(ExecutionJournal.MAX_LINES_PER_SOURCE);
        assertThat(events.of(12L, Kind.EVENT)).singleElement().satisfies(omitted -> {
            assertThat(omitted.getComponentKey()).isEqualTo(ORDERS);
            assertThat(omitted.getPayload()).isEqualTo(Map.of("type", "LOGS_OMITTED", "count", 30));
        });
    }

    @Test
    void remembersTheLastStateAndResponseOfEachNode() {
        journal.recordStates(12L, Map.of(ORDERS, ComponentStatus.STARTING), START);
        journal.recordResponse(12L, ORDERS, "{\"component\":\"orders-api\"}", START);
        journal.recordStates(12L, Map.of(ORDERS, ComponentStatus.HEALTHY), START);

        assertThat(journal.nodes(12L)).containsExactly(Map.entry(ORDERS,
                new ExecutionView.Node(ComponentStatus.HEALTHY, "{\"component\":\"orders-api\"}")));
        assertThat(journal.nodes(13L)).isEmpty();
    }

    @Test
    void recordsOnlyWhatAChangeAltered() {
        ExecutionJournal.Mark before = ExecutionJournal.mark(execution);
        execution.startStarting();
        journal.recordChanges(before, execution, START);

        before = ExecutionJournal.mark(execution);
        journal.recordChanges(before, execution, START);

        assertThat(events.of(12L)).extracting(ExecutionEvent::getPayload)
                .containsExactly(Map.of("type", "PHASE", "phase", "STARTING"));
    }

    private static List<LogLine> lines(int count) {
        List<LogLine> lines = new ArrayList<>();
        for (int line = 0; line < count; line++) {
            lines.add(new LogLine(ORDERS, START.plusMillis(line), "line " + line));
        }
        return lines;
    }
}
