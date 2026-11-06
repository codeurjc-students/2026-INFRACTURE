package es.codeurjc.infracture.execution.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Runs the plan with the docker CLI. JSON is valid YAML, so the plan
// goes to Compose on stdin and no file is written.
@Component
class ComposeRuntimeEngine implements RuntimeEngine {

    static final Duration START_TIMEOUT = Duration.ofMinutes(5);
    static final Duration STOP_TIMEOUT = Duration.ofMinutes(2);
    // Containers first: Docker keeps a network or volume that is in use.
    private static final List<String> RESOURCE_KINDS = List.of("container", "network", "volume");
    // Compose prints the cause last, so the reason keeps the end.
    private static final int MAX_OUTPUT_IN_REASON = 500;

    private final JsonMapper jsonMapper;
    // The backend's own container in the distribution; empty when it runs on
    // the host, where it cannot join the execution network.
    private final String backendContainer;

    ComposeRuntimeEngine(JsonMapper jsonMapper,
            @Value("${infracture.execution.backend-container:}") String backendContainer) {
        this.jsonMapper = jsonMapper;
        this.backendContainer = backendContainer;
    }

    @Override
    public void up(Map<String, Object> plan) {
        // One minute more than --wait, so Compose names the unhealthy service.
        compose(plan, START_TIMEOUT.plusMinutes(1),
                "up", "--wait", "--wait-timeout", String.valueOf(START_TIMEOUT.toSeconds()));
        if (!backendContainer.isBlank()) {
            docker(List.of("network", "connect", network(plan), backendContainer));
        }
    }

    @Override
    public Map<String, ComponentStatus> status(Map<String, Object> plan) {
        Map<String, ComponentStatus> statuses = new LinkedHashMap<>();
        // Compose prints one JSON object per container and line.
        for (String line : compose(plan, STOP_TIMEOUT, "ps", "--all", "--format", "json").split("\\R")) {
            if (!line.isBlank()) {
                PsLine container = jsonMapper.readValue(line, PsLine.class);
                statuses.put(container.service(), ComponentStatus.of(container.state(), container.health()));
            }
        }
        return statuses;
    }

    @Override
    public void down(Map<String, Object> plan, Map<String, String> labels) {
        // Docker cannot remove the network while the backend is still in it.
        if (!backendContainer.isBlank()) {
            try {
                docker(List.of("network", "disconnect", "--force", network(plan), backendContainer));
            } catch (RuntimeEngineException alreadyDetached) {
                // Never attached, or the network is gone: nothing keeps it busy.
            }
        }
        compose(plan, STOP_TIMEOUT, "down", "-v");
        // Also removes what a start cut short left outside Compose.
        for (String kind : RESOURCE_KINDS) {
            List<String> ids = find(kind, labels);
            if (!ids.isEmpty()) {
                List<String> remove = new ArrayList<>(List.of(kind, "rm", "-f"));
                remove.addAll(ids);
                docker(remove);
            }
        }
        int left = 0;
        for (String kind : RESOURCE_KINDS) {
            left += find(kind, labels).size();
        }
        if (left > 0) {
            throw new RuntimeEngineException(
                    "Cleanup left %d Docker resources with the execution labels".formatted(left));
        }
    }

    // -p wins over COMPOSE_PROJECT_NAME, so the plan's name is the project.
    // docker compose (up/down) write the command --> execute
    private String compose(Map<String, Object> plan, Duration timeout, String verb, String... flags) {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "--ansi", "never",
                "-p", String.valueOf(plan.get("name")), "-f", "-", verb));
        command.addAll(List.of(flags));
        return execute("docker compose " + verb, command, jsonMapper.writeValueAsString(plan), timeout);
    }

    // Compose names each network after the project: <project>_<network>.
    private static String network(Map<String, Object> plan) {
        return plan.get("name") + "_" + PlanCompiler.NETWORK;
    }

    //only docker comands not compose
    private static String docker(List<String> arguments) {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(arguments);
        return execute("docker " + arguments.get(0) + " " + arguments.get(1), command, null, STOP_TIMEOUT);
    }

    private static List<String> find(String kind, Map<String, String> labels) {
        List<String> arguments = new ArrayList<>(List.of(kind, "ls", "-q"));
        if (kind.equals("container")) {
            arguments.add("--all");
        }
        // Every filter must match, so other instances are never listed.
        for (Map.Entry<String, String> label : labels.entrySet()) {
            arguments.add("--filter");
            arguments.add("label=" + label.getKey() + "=" + label.getValue());
        }
        List<String> ids = new ArrayList<>();
        for (String line : docker(arguments).split("\\R")) {
            if (!line.isBlank()) {
                ids.add(line.strip());
            }
        }
        return ids;
    }

    // Output goes to a file: an unread pipe could block the process.
    private static String execute(String step, List<String> command, String input, Duration timeout) {
        Path output = null;
        try {
            output = Files.createTempFile("infracture-docker-", ".log");
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            try (OutputStream stdin = process.getOutputStream()) {
                if (input != null) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RuntimeEngineException(
                        "%s did not finish within %d seconds".formatted(step, timeout.toSeconds()));
            }
            String text = Files.readString(output);
            if (process.exitValue() != 0) {
                throw new RuntimeEngineException(step + " failed: " + tail(text));
            }
            return text;
        } catch (IOException exception) {
            throw new RuntimeEngineException(step + " could not run: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeEngineException(step + " was interrupted", exception);
        } finally {
            deleteQuietly(output);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PsLine(@JsonProperty("Service") String service, @JsonProperty("State") String state,
            @JsonProperty("Health") String health) {
    }

    private static String tail(String text) {
        String trimmed = text.strip();
        return trimmed.length() > MAX_OUTPUT_IN_REASON
                ? trimmed.substring(trimmed.length() - MAX_OUTPUT_IN_REASON)
                : trimmed;
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A leftover temporary log does not affect the execution.
        }
    }
}
