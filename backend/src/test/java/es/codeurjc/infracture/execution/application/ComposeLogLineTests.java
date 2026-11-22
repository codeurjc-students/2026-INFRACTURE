package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ComposeLogLineTests {

    @Test
    void keepsTheHyphensOfTheServiceAndEverythingAfterTheTime() {
        LogLine line = ComposeRuntimeEngine.parseLogLine(
                "orders-api-12  | 2026-11-22T10:00:05.123456789Z GET /orders | 200");

        assertThat(line).isEqualTo(new LogLine("orders-api", Instant.parse("2026-11-22T10:00:05.123456789Z"),
                "GET /orders | 200"));
    }

    @Test
    void readsALineWithNoText() {
        assertThat(ComposeRuntimeEngine.parseLogLine("load-1  | 2026-11-22T10:00:05Z"))
                .isEqualTo(new LogLine("load", Instant.parse("2026-11-22T10:00:05Z"), ""));
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
    void readsAVeryLongLineInOnePass() {
        String text = "-1 ".repeat(200_000);

        assertThat(ComposeRuntimeEngine.parseLogLine("probe-1  | 2026-11-22T10:00:05Z " + text).text())
                .isEqualTo(text);
    }
}
