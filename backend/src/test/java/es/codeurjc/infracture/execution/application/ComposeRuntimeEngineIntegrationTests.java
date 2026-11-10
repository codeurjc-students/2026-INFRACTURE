package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import es.codeurjc.infracture.execution.domain.LoadSummary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// Runs the real docker CLI against the Docker engine that Testcontainers also uses. A small busybox
// plan stands in for the demo image, which CI does not build.
class ComposeRuntimeEngineIntegrationTests {

    private final String instance = "test-" + UUID.randomUUID().toString().replace("-", "");
    private final Map<String, String> labels =
            Map.of(PlanCompiler.INSTANCE_LABEL, instance, PlanCompiler.EXECUTION_LABEL, "1");
    private final ComposeRuntimeEngine engine = new ComposeRuntimeEngine(JsonMapper.builder().build(), "");
    private static final LoadSummary SUMMARY = new LoadSummary(3L, 0L, 0L, 1.5, 2.0);
    private static final BooleanSupplier NEVER = () -> false;

    @AfterEach
    void removeEverything() {
        engine.down(plan("true"), labels);
    }

    @Test
    void startsAHealthyPlanAndLeavesNothingAfterDown() {
        engine.up(plan("true"), NEVER);

        assertThat(docker("container", "ls", "-q", "--filter", "health=healthy", "--filter", label())).hasSize(1);
        assertThat(docker("network", "ls", "-q", "--filter", label())).hasSize(1);

        engine.down(plan("true"), labels);

        assertThat(labelled()).isEmpty();
    }

    @Test
    void reportsTheStatusOfEachService() {
        engine.up(plan("true"), NEVER);
        assertThat(engine.status(plan("true"))).containsExactly(Map.entry("probe", ComponentStatus.HEALTHY));

        docker("container", "stop", "-t", "1", "infracture-" + instance + "-exec-1-probe-1");

        assertThat(engine.status(plan("true"))).containsExactly(Map.entry("probe", ComponentStatus.EXITED));
    }

    @Test
    void joinsTheBackendToTheNetworkAndLeavesItBeforeCleanup() {
        String backend = "infracture-" + instance + "-backend";
        docker("container", "run", "-d", "--quiet", "--name", backend, "busybox:1.37", "sleep", "300");
        ComposeRuntimeEngine inContainer = new ComposeRuntimeEngine(JsonMapper.builder().build(), backend);
        try {
            inContainer.up(plan("true"), NEVER);
            assertThat(docker("container", "inspect", "-f", "{{range $name, $n := .NetworkSettings.Networks}}"
                    + "{{$name}} {{end}}", backend).getFirst())
                    .contains("infracture-" + instance + "-exec-1_exec");

            inContainer.down(plan("true"), labels);

            assertThat(labelled()).isEmpty();
        } finally {
            docker("container", "rm", "-f", backend);
        }
    }

    @Test
    void startsTheLoadOnlyWhenAskedAndKeepsItsSummaryWhenStopped() {
        Map<String, Object> plan = withLoad("while true; do sleep 1; done");
        engine.up(plan, NEVER);
        assertThat(engine.status(plan)).containsOnlyKeys("probe");
        assertThat(engine.stopLoad(plan)).isEqualTo(new LoadReport(null, null));

        engine.startLoad(plan, NEVER);
        assertThat(engine.status(plan)).containsEntry(PlanCompiler.LOAD_SERVICE, ComponentStatus.HEALTHY);

        LoadReport report = engine.stopLoad(plan);
        assertThat(report).isEqualTo(new LoadReport(105, SUMMARY));
        assertThat(report.completed()).isFalse();
        assertThat(engine.stopLoad(plan)).isEqualTo(report);

        engine.down(plan, labels);
        assertThat(labelled()).isEmpty();
    }

    @Test
    void readsTheSummaryOfALoadThatEndedOnItsOwn() throws InterruptedException {
        Map<String, Object> plan = withLoad("true");
        engine.up(plan, NEVER);
        engine.startLoad(plan, NEVER);
        while (engine.status(plan).get(PlanCompiler.LOAD_SERVICE) != ComponentStatus.EXITED) {
            Thread.sleep(200);
        }

        assertThat(engine.stopLoad(plan)).isEqualTo(new LoadReport(0, SUMMARY));
    }

    @Test
    void reportsAnUnhealthyServiceAndCleansUpWhatItCreated() {
        assertThatThrownBy(() -> engine.up(plan("false"), NEVER))
                .isInstanceOf(RuntimeEngineException.class)
                .hasMessageStartingWith("docker compose up failed:")
                .hasMessageContaining("unhealthy");

        engine.down(plan("false"), labels);

        assertThat(labelled()).isEmpty();
    }

    // The first health check runs only after its interval, so the start waits until Stop cancels it.
    @Test
    void cancelsAStartThatWaitsForHealthAndLeavesNothingAfterDown() {
        Map<String, Object> slow = plan("true", "300s");
        Instant stopAt = Instant.now().plusSeconds(2);

        assertThatThrownBy(() -> engine.up(slow, () -> Instant.now().isAfter(stopAt)))
                .isInstanceOf(StartCancelledException.class)
                .hasMessage("docker compose up was cancelled because the execution is stopping");
        assertThat(Duration.between(stopAt, Instant.now())).isLessThan(Duration.ofSeconds(30));

        engine.down(slow, labels);

        assertThat(labelled()).isEmpty();
    }

    // A volume of the execution that a container of someone else uses cannot be removed. The cleanup fails,
    // leaves that container alone and succeeds once the volume is free.
    @Test
    void failsTheCleanupOfAVolumeInUseAndRetriesItWithoutTouchingOtherContainers() {
        String volume = docker("volume", "create", "--label", label(PlanCompiler.INSTANCE_LABEL),
                "--label", label(PlanCompiler.EXECUTION_LABEL)).getLast();
        String other = docker("container", "run", "-d", "--quiet", "-v", volume + ":/data", "busybox:1.37",
                "sleep", "300").getLast();
        try {
            assertThatThrownBy(() -> engine.down(plan("true"), labels))
                    .isInstanceOf(RuntimeEngineException.class)
                    .hasMessageStartingWith("docker volume rm failed:");
            assertThat(docker("container", "ls", "-q", "--no-trunc", "--filter", "id=" + other)).hasSize(1);
        } finally {
            docker("container", "rm", "-f", other);
        }

        engine.down(plan("true"), labels);

        assertThat(labelled()).isEmpty();
    }

    @Test
    void removesLabelledContainersThatComposeDoesNotKnow() {
        docker("container", "run", "-d", "--quiet", "--label", label(PlanCompiler.INSTANCE_LABEL),
                "--label", label(PlanCompiler.EXECUTION_LABEL), "busybox:1.37", "sleep", "300");

        engine.down(plan("true"), labels);

        assertThat(labelled()).isEmpty();
    }

    @Test
    void leavesContainersOfOtherExecutionsAlone() {
        String other = docker("container", "run", "-d", "--quiet", "--label", label(PlanCompiler.INSTANCE_LABEL),
                "--label", PlanCompiler.EXECUTION_LABEL + "=2", "busybox:1.37", "sleep", "300").getLast();
        try {
            engine.down(plan("true"), labels);

            assertThat(docker("container", "ls", "-aq", "--no-trunc", "--filter", "id=" + other)).hasSize(1);
        } finally {
            docker("container", "rm", "-f", other);
        }
    }

    private Map<String, Object> plan(String healthCommand) {
        return plan(healthCommand, "1s");
    }

    private Map<String, Object> plan(String healthCommand, String healthInterval) {
        Map<String, Object> service = Map.of(
                "image", "busybox:1.37",
                "command", List.of("sleep", "300"),
                // An init process passes the stop signal on, so down does not wait for its timeout.
                "init", true,
                "healthcheck", Map.of("test", List.of("CMD", healthCommand), "interval", healthInterval, "retries", 1),
                "networks", List.of(PlanCompiler.NETWORK),
                "labels", labels);
        return Map.of(
                "name", "infracture-" + instance + "-exec-1",
                "networks", Map.of(PlanCompiler.NETWORK, Map.of("labels", labels)),
                "services", Map.of("probe", service));
    }

    // A shell stands in for k6: it prints the summary line when it ends, as the load scripts do.
    private Map<String, Object> withLoad(String body) {
        String summary = ComposeRuntimeEngine.SUMMARY_MARKER + "{\"requests\":3,\"failedRequests\":0,"
                + "\"droppedIterations\":0,\"averageLatencyMs\":1.5,\"p95LatencyMs\":2.0}";
        Map<String, Object> load = Map.of(
                "image", "busybox:1.37",
                "command", List.of("sh", "-c", "finish() { echo '" + summary + "'; }; "
                        + "trap 'finish; exit 105' TERM; " + body + "; finish"),
                "profiles", List.of(PlanCompiler.LOAD_PROFILE),
                "networks", List.of(PlanCompiler.NETWORK),
                "labels", labels);
        Map<String, Object> plan = new HashMap<>(plan("true"));
        @SuppressWarnings("unchecked")
        Map<String, Object> services = new HashMap<>((Map<String, Object>) plan.get("services"));
        services.put(PlanCompiler.LOAD_SERVICE, load);
        plan.put("services", services);
        return plan;
    }

    private List<String> labelled() {
        List<String> ids = new ArrayList<>();
        ids.addAll(docker("container", "ls", "-aq", "--filter", label()));
        ids.addAll(docker("network", "ls", "-q", "--filter", label()));
        ids.addAll(docker("volume", "ls", "-q", "--filter", label()));
        return ids;
    }

    private String label() {
        return "label=" + PlanCompiler.INSTANCE_LABEL + "=" + instance;
    }

    private String label(String key) {
        return key + "=" + labels.get(key);
    }

    private static List<String> docker(String... arguments) {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.waitFor()).as(String.join(" ", command) + "\n" + output).isZero();
            List<String> lines = new ArrayList<>();
            for (String line : output.split("\\R")) {
                if (!line.isBlank()) {
                    lines.add(line.strip());
                }
            }
            return lines;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
