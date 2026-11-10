package es.codeurjc.infracture.execution.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import es.codeurjc.infracture.execution.domain.LoadSummary;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
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
    // Activates the load service. Only the first `up` leaves it out, so it starts after the nodes.
    private static final List<String> WITH_LOAD = List.of("--profile", PlanCompiler.LOAD_PROFILE);
    // Longer than the script's graceful stop, so k6 has time to print its summary.
    private static final String LOAD_STOP_SECONDS = "15";
    // The line the load scripts print with their summary (load-generator/scripts).
    static final String SUMMARY_MARKER = "INFRACTURE_SUMMARY ";
    // How often a start asks whether it must stop, and how long a stopped command has to exit cleanly.
    private static final Duration CANCEL_CHECK = Duration.ofSeconds(1);
    private static final Duration GRACEFUL_EXIT = Duration.ofSeconds(10);
    private static final BooleanSupplier NEVER = () -> false;

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
    public void up(Map<String, Object> plan, BooleanSupplier cancelled) {
        // One minute more than --wait, so Compose names the unhealthy service.
        compose(plan, START_TIMEOUT.plusMinutes(1), cancelled, List.of(),
                "up", "--wait", "--wait-timeout", String.valueOf(START_TIMEOUT.toSeconds()));
        if (!backendContainer.isBlank()) {
            backendNetwork(plan, "connect");
        }
    }

    // --no-deps: the nodes are already healthy, so Compose only starts k6.
    @Override
    public void startLoad(Map<String, Object> plan, BooleanSupplier cancelled) {
        compose(plan, START_TIMEOUT, cancelled, WITH_LOAD, "up", "-d", "--no-deps", PlanCompiler.LOAD_SERVICE);
    }

    @Override
    public LoadReport stopLoad(Map<String, Object> plan) {
        compose(plan, STOP_TIMEOUT, WITH_LOAD, "stop", "--timeout", LOAD_STOP_SECONDS, PlanCompiler.LOAD_SERVICE);
        Integer exitCode = null;
        for (PsLine container : ps(plan, PlanCompiler.LOAD_SERVICE)) {
            exitCode = container.exitCode();
        }
        LoadSummary summary = null;
        String logs = compose(plan, STOP_TIMEOUT, WITH_LOAD, "logs", "--no-color", "--no-log-prefix",
                PlanCompiler.LOAD_SERVICE);
        for (String line : logs.split("\\R")) {
            if (line.startsWith(SUMMARY_MARKER)) {
                summary = jsonMapper.readValue(line.substring(SUMMARY_MARKER.length()), LoadSummary.class);
            }
        }
        return new LoadReport(exitCode, summary);
    }

    @Override
    public Map<String, ComponentStatus> status(Map<String, Object> plan) {
        Map<String, ComponentStatus> statuses = new LinkedHashMap<>();
        for (PsLine container : ps(plan)) {
            statuses.put(container.service(), ComponentStatus.of(container.state(), container.health()));
        }
        return statuses;
    }

    // Compose prints one JSON object per container and line.
    private List<PsLine> ps(Map<String, Object> plan, String... services) {
        List<String> flags = new ArrayList<>(List.of("--all", "--format", "json"));
        flags.addAll(List.of(services));
        List<PsLine> containers = new ArrayList<>();
        String output = compose(plan, STOP_TIMEOUT, WITH_LOAD, "ps", flags.toArray(String[]::new));
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                containers.add(jsonMapper.readValue(line, PsLine.class));
            }
        }
        return containers;
    }

    @Override
    public void down(Map<String, Object> plan, Map<String, String> labels) {
        // Docker cannot remove the network while the backend is still in it.
        if (!backendContainer.isBlank()) {
            try {
                backendNetwork(plan, "disconnect", "--force");
            } catch (RuntimeEngineException _) {
                // Never attached, or the network is gone: nothing keeps it busy.
            }
        }
        compose(plan, STOP_TIMEOUT, WITH_LOAD, "down", "-v");
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
    private String compose(Map<String, Object> plan, Duration timeout, List<String> profiles, String verb,
            String... flags) {
        return compose(plan, timeout, NEVER, profiles, verb, flags);
    }

    private String compose(Map<String, Object> plan, Duration timeout, BooleanSupplier cancelled,
            List<String> profiles, String verb, String... flags) {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "--ansi", "never",
                "-p", String.valueOf(plan.get("name")), "-f", "-"));
        command.addAll(profiles);
        command.add(verb);
        command.addAll(List.of(flags));
        return execute("docker compose " + verb, command, jsonMapper.writeValueAsString(plan), timeout, cancelled);
    }

    private void backendNetwork(Map<String, Object> plan, String... action) {
        List<String> arguments = new ArrayList<>(List.of("network"));
        arguments.addAll(List.of(action));
        arguments.add(network(plan));
        arguments.add(backendContainer);
        docker(arguments);
    }

    // Compose names each network after the project: <project>_<network>.
    private static String network(Map<String, Object> plan) {
        return plan.get("name") + "_" + PlanCompiler.NETWORK;
    }

    //only docker comands not compose
    private static String docker(List<String> arguments) {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(arguments);
        return execute("docker " + arguments.get(0) + " " + arguments.get(1), command, null, STOP_TIMEOUT, NEVER);
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

    // The output is read while the process runs: an unread pipe fills up and
    // blocks the process. A cancelled or late command is ended, not left running.
    private static String execute(String step, List<String> command, String input, Duration timeout,
            BooleanSupplier cancelled) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            FutureTask<String> output = new FutureTask<>(
                    () -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Thread.ofVirtual().start(output);
            try (OutputStream stdin = process.getOutputStream()) {
                if (input != null) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                }
            }
            Instant deadline = Instant.now().plus(timeout);
            while (!process.waitFor(CANCEL_CHECK.toMillis(), TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean()) {
                    end(process);
                    throw new StartCancelledException(step + " was cancelled because the execution is stopping");
                }
                if (!Instant.now().isBefore(deadline)) {
                    end(process);
                    throw new RuntimeEngineException(
                            "%s did not finish within %d seconds".formatted(step, timeout.toSeconds()));
                }
            }
            String text = output.get();
            if (process.exitValue() != 0) {
                throw new RuntimeEngineException(step + " failed: " + tail(text));
            }
            return text;
        } catch (IOException | ExecutionException exception) {
            throw new RuntimeEngineException(step + " could not run: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeEngineException(step + " was interrupted", exception);
        }
    }

    // `docker compose` runs as a child of the docker CLI, so the child is ended too. SIGTERM first lets
    // Compose stop what it was doing; a command that ignores it is killed.
    private static void end(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        if (!process.waitFor(GRACEFUL_EXIT.toSeconds(), TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PsLine(@JsonProperty("Service") String service, @JsonProperty("State") String state,
            @JsonProperty("Health") String health, @JsonProperty("ExitCode") Integer exitCode) {
    }

    private static String tail(String text) {
        String trimmed = text.strip();
        return trimmed.length() > MAX_OUTPUT_IN_REASON
                ? trimmed.substring(trimmed.length() - MAX_OUTPUT_IN_REASON)
                : trimmed;
    }
}
