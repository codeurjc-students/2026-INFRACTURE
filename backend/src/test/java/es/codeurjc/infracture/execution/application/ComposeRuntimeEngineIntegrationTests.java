package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @AfterEach
    void removeEverything() {
        engine.down(plan("true"), labels);
    }

    @Test
    void startsAHealthyPlanAndLeavesNothingAfterDown() {
        engine.up(plan("true"));

        assertThat(docker("container", "ls", "-q", "--filter", "health=healthy", "--filter", label())).hasSize(1);
        assertThat(docker("network", "ls", "-q", "--filter", label())).hasSize(1);

        engine.down(plan("true"), labels);

        assertThat(labelled()).isEmpty();
    }

    @Test
    void reportsTheStatusOfEachService() {
        engine.up(plan("true"));
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
            inContainer.up(plan("true"));
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
    void reportsAnUnhealthyServiceAndCleansUpWhatItCreated() {
        assertThatThrownBy(() -> engine.up(plan("false")))
                .isInstanceOf(RuntimeEngineException.class)
                .hasMessageStartingWith("docker compose up failed:")
                .hasMessageContaining("unhealthy");

        engine.down(plan("false"), labels);

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
        Map<String, Object> service = Map.of(
                "image", "busybox:1.37",
                "command", List.of("sleep", "300"),
                // An init process passes the stop signal on, so down does not wait for its timeout.
                "init", true,
                "healthcheck", Map.of("test", List.of("CMD", healthCommand), "interval", "1s", "retries", 1),
                "networks", List.of(PlanCompiler.NETWORK),
                "labels", labels);
        return Map.of(
                "name", "infracture-" + instance + "-exec-1",
                "networks", Map.of(PlanCompiler.NETWORK, Map.of("labels", labels)),
                "services", Map.of("probe", service));
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
