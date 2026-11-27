package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Container;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.HttpProbe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Limits;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Wiring;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComposeLogLineTests {

    @Test
    void keepsTheHyphensOfTheServiceAndEverythingAfterTheTime() {
        ComposeRuntimeEngine.ComposeLine line = ComposeRuntimeEngine.parseLogLine(
                "orders-api-12  | 2026-11-22T10:00:05.123456789Z GET /orders | 200");

        assertThat(line).isEqualTo(new ComposeRuntimeEngine.ComposeLine("orders-api",
                Instant.parse("2026-11-22T10:00:05.123456789Z"), "GET /orders | 200"));
    }

    @Test
    void readsALineWithNoText() {
        assertThat(ComposeRuntimeEngine.parseLogLine("load-1  | 2026-11-22T10:00:05Z"))
                .isEqualTo(new ComposeRuntimeEngine.ComposeLine("load", Instant.parse("2026-11-22T10:00:05Z"), ""));
    }

    @Test
    void ignoresLinesThatAreNotAContainerLine() {
        assertThat(ComposeRuntimeEngine.parseLogLine("Attaching to orders-api-1")).isNull();
        assertThat(ComposeRuntimeEngine.parseLogLine("orders-api  | 2026-11-22T10:00:05Z no index")).isNull();
        assertThat(ComposeRuntimeEngine.parseLogLine("-1  | 2026-11-22T10:00:05Z no service")).isNull();
        assertThat(ComposeRuntimeEngine.parseLogLine("orders-api-1  | ")).isNull();
        assertThat(ComposeRuntimeEngine.parseLogLine("")).isNull();
    }

    @Test
    void namesEachLineByItsNodeTheLoadByNoneAndDropsServicesThePlanDoesNotHave() {
        UUID orders = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
        Node node = new Node(orders, "c-" + orders, new Container("image", null, null, List.of()),
                new Wiring(Map.of(), Map.of(), List.of()), new HttpProbe(8080, "/health", 2, 2, 15, 60), null,
                new Limits(0.5, 384L << 20, 200));
        ExecutionPlan plan = new ExecutionPlan("project", Map.of(), List.of(node), null);

        List<LogLine> lines = ComposeRuntimeEngine.logLines(plan, String.join("\n",
                "c-" + orders + "-1  | 2026-11-22T10:00:05Z Started DemoApplication",
                "workload-1  | 2026-11-22T10:00:06Z running (01m00s)",
                "unknown-1  | 2026-11-22T10:00:07Z not part of the plan"));

        assertThat(lines).containsExactly(
                new LogLine(orders, Instant.parse("2026-11-22T10:00:05Z"), "Started DemoApplication"),
                new LogLine(null, Instant.parse("2026-11-22T10:00:06Z"), "running (01m00s)"));
    }

    @Test
    void readsAVeryLongLineInOnePass() {
        String text = "-1 ".repeat(200_000);

        assertThat(ComposeRuntimeEngine.parseLogLine("probe-1  | 2026-11-22T10:00:05Z " + text).text())
                .isEqualTo(text);
    }
}
