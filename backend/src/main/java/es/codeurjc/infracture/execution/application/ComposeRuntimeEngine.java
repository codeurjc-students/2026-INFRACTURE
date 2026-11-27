package es.codeurjc.infracture.execution.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.CommandProbe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Container;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.HttpProbe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Limits;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Load;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Probe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.RunAs;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Wiring;
import es.codeurjc.infracture.execution.domain.LoadSummary;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Runs the plan with the docker CLI. It is the only place that knows Compose: it renders the plan as a
// Compose file and reads Compose's answers back as node keys. JSON is valid YAML, so the rendered file goes
// to Compose on stdin and no file is written.
@Component
class ComposeRuntimeEngine implements RuntimeEngine {

    static final Duration START_TIMEOUT = Duration.ofMinutes(5);
    static final Duration STOP_TIMEOUT = Duration.ofMinutes(2);
    // Containers first: Docker keeps a network or volume that is in use.
    private static final List<String> RESOURCE_KINDS = List.of("container", "network", "volume");
    // Compose prints the cause last, so the reason keeps the end.
    private static final int MAX_OUTPUT_IN_REASON = 500;
    // Each plan is one Compose project with one network. Compose names it <project>_<network>.
    private static final String NETWORK = "exec";
    // The k6 service. Its Compose profile keeps `up --wait` from starting it with the nodes.
    private static final String LOAD_SERVICE = "workload";
    private static final String LOAD_PROFILE = "load";
    // Activates the load service. Only the first `up` leaves it out, so it starts after the nodes.
    private static final List<String> WITH_LOAD = List.of("--profile", LOAD_PROFILE);
    // Longer than the script's graceful stop, so k6 has time to print its summary.
    private static final String LOAD_STOP_SECONDS = "15";
    // The line the load scripts print with their summary (load-generator/scripts).
    static final String SUMMARY_MARKER = "INFRACTURE_SUMMARY ";
    // How often a start asks whether it must stop, and how long a stopped command has to exit cleanly.
    private static final Duration CANCEL_CHECK = Duration.ofSeconds(1);
    private static final Duration GRACEFUL_EXIT = Duration.ofSeconds(10);
    private static final BooleanSupplier NEVER = () -> false;
    // docker stats needs about two seconds. A Docker that does not answer costs one sample, not the loop.
    private static final Duration USAGE_TIMEOUT = Duration.ofSeconds(4);
    private static final String STOPPED_MEMORY = "0B / 0B";
    private static final Pattern MEMORY_USED = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(B|KiB|MiB|GiB|TiB) ");
    private static final Map<String, Long> MEMORY_UNITS = Map.of("B", 1L, "KiB", 1L << 10, "MiB", 1L << 20,
            "GiB", 1L << 30, "TiB", 1L << 40);

    private final JsonMapper jsonMapper;
    private final ExecutionCredentials credentials;
    private final String backendContainer;

    ComposeRuntimeEngine(JsonMapper jsonMapper, ExecutionCredentials credentials,
            @Value("${infracture.execution.backend-container:}") String backendContainer) {
        this.jsonMapper = jsonMapper;
        this.credentials = credentials;
        this.backendContainer = backendContainer;
    }

    @Override
    public void up(ExecutionPlan plan, BooleanSupplier cancelled) {
        // One minute more than --wait, so Compose names the unhealthy service. -d keeps Compose from
        // attaching to the containers (/attach, /events), which docker-proxy forbids.
        compose(plan, START_TIMEOUT.plusMinutes(1), cancelled, List.of(),
                "up", "-d", "--wait", "--wait-timeout", String.valueOf(START_TIMEOUT.toSeconds()));
        // The lowest priority keeps the backend's default route on its own network. Otherwise Docker may
        // move it to the experiment's network, and on Linux the connections already open, such as event
        // streams, stop getting their replies.
        if (!backendContainer.isBlank()) {
            backendNetwork(plan, "connect", "--gw-priority", "-1");
        }
    }

    // --no-deps: the nodes are already healthy, so Compose only starts k6.
    @Override
    public void startLoad(ExecutionPlan plan, BooleanSupplier cancelled) {
        compose(plan, START_TIMEOUT, cancelled, WITH_LOAD, "up", "-d", "--no-deps", LOAD_SERVICE);
    }

    @Override
    public LoadReport stopLoad(ExecutionPlan plan) {
        compose(plan, STOP_TIMEOUT, WITH_LOAD, "stop", "--timeout", LOAD_STOP_SECONDS, LOAD_SERVICE);
        Integer exitCode = null;
        for (PsLine container : ps(plan, LOAD_SERVICE)) {
            exitCode = container.exitCode();
        }
        LoadSummary summary = null;
        String logs = compose(plan, STOP_TIMEOUT, WITH_LOAD, "logs", "--no-color", "--no-log-prefix",
                LOAD_SERVICE);
        for (String line : logs.split("\\R")) {
            if (line.startsWith(SUMMARY_MARKER)) {
                summary = jsonMapper.readValue(line.substring(SUMMARY_MARKER.length()), LoadSummary.class);
            }
        }
        return new LoadReport(exitCode, summary);
    }

    @Override
    public RuntimeStatus status(ExecutionPlan plan) {
        Map<String, UUID> nodes = nodesByService(plan);
        Map<UUID, ComponentStatus> statuses = new LinkedHashMap<>();
        ComponentStatus load = null;
        for (PsLine container : ps(plan)) {
            ComponentStatus status = ComponentStatus.of(container.state(), container.health());
            if (LOAD_SERVICE.equals(container.service())) {
                load = status;
            } else if (nodes.containsKey(container.service())) {
                statuses.put(nodes.get(container.service()), status);
            }
        }
        return new RuntimeStatus(statuses, load);
    }

    @Override
    public List<LogLine> logs(ExecutionPlan plan, Instant since) {
        List<String> flags = new ArrayList<>(List.of("--no-color", "--timestamps"));
        if (since != null) {
            flags.addAll(List.of("--since", since.toString()));
        }
        return logLines(plan, compose(plan, STOP_TIMEOUT, WITH_LOAD, "logs", flags.toArray(String[]::new)));
    }

    // Names each line of `docker compose logs` by its node, or null for the load. A service the plan does not
    // have is not this execution's, so its lines are dropped.
    static List<LogLine> logLines(ExecutionPlan plan, String output) {
        Map<String, UUID> nodes = nodesByService(plan);
        List<LogLine> lines = new ArrayList<>();
        for (String line : output.split("\\R")) {
            ComposeLine parsed = parseLogLine(line);
            if (parsed == null) {
                continue;
            }
            if (LOAD_SERVICE.equals(parsed.service())) {
                lines.add(new LogLine(null, parsed.at(), parsed.text()));
            } else if (nodes.containsKey(parsed.service())) {
                lines.add(new LogLine(nodes.get(parsed.service()), parsed.at(), parsed.text()));
            }
        }
        return lines;
    }

    // One command reads every container at once. A name that no longer exists fails the whole command, so
    // only the running containers are asked for.
    @Override
    public Map<UUID, ResourceUsage> usage(ExecutionPlan plan) {
        Map<String, UUID> nodes = nodesByService(plan);
        Map<String, UUID> services = new HashMap<>();
        for (PsLine container : ps(plan, USAGE_TIMEOUT)) {
            if ("running".equals(container.state()) && nodes.containsKey(container.service())) {
                services.put(container.name(), nodes.get(container.service()));
            }
        }
        if (services.isEmpty()) {
            return Map.of();
        }
        List<String> command = new ArrayList<>(List.of("docker", "stats", "--no-stream", "--format", "json"));
        command.addAll(services.keySet());
        Map<UUID, ResourceUsage> usage = new LinkedHashMap<>();
        for (String line : execute("docker stats", command, null, Map.of(), USAGE_TIMEOUT, NEVER).split("\\R")) {
            if (line.startsWith("{")) {
                StatsLine stats = jsonMapper.readValue(line, StatsLine.class);
                ResourceUsage reading = parseUsage(stats.cpu(), stats.memory());
                if (services.containsKey(stats.name())
                        && (reading.cpuPercent() != null || reading.memoryBytes() != null)) {
                    usage.put(services.get(stats.name()), reading);
                }
            }
        }
        return usage;
    }

    // Docker prints "49.60%" and "572KiB / 384MiB", or "--" when it has no value. A container that is not
    // running prints 0.00% and 0B / 0B: that is no reading, not a real zero.
    static ResourceUsage parseUsage(String cpu, String memory) {
        if (STOPPED_MEMORY.equals(memory)) {
            return new ResourceUsage(null, null);
        }
        Double percent = cpu.endsWith("%") ? Double.valueOf(cpu.substring(0, cpu.length() - 1)) : null;
        Matcher used = MEMORY_USED.matcher(memory);
        Long bytes = used.lookingAt()
                ? Math.round(Double.parseDouble(used.group(1)) * MEMORY_UNITS.get(used.group(2)))
                : null;
        return new ResourceUsage(percent, bytes);
    }

    // With --timestamps each line reads "<service>-<index> | <time> <text>". The service keeps its own
    // hyphens and only the last "-<index>" goes. Null for any other line. Plain index searches, not a regex,
    // so a long line is read in one pass.
    static ComposeLine parseLogLine(String line) {
        int bar = line.indexOf(" | ");
        if (bar < 0) {
            return null;
        }
        String container = line.substring(0, bar).strip();
        int dash = container.lastIndexOf('-');
        String index = container.substring(dash + 1);
        if (dash <= 0 || index.isEmpty() || !index.chars().allMatch(Character::isDigit)) {
            return null;
        }
        String rest = line.substring(bar + 3);
        int space = rest.indexOf(' ');
        String time = space < 0 ? rest : rest.substring(0, space);
        if (time.isEmpty()) {
            return null;
        }
        String text = space < 0 ? "" : rest.substring(space + 1);
        return new ComposeLine(container.substring(0, dash), Instant.parse(time), text);
    }

    private record Secret(UUID owner) {
    }

    // One line of `docker compose logs`, still named by its Compose service.
    record ComposeLine(String service, Instant at, String text) {
    }

    // Compose prints one JSON object per container and line.
    private List<PsLine> ps(ExecutionPlan plan, String... services) {
        return ps(plan, STOP_TIMEOUT, services);
    }

    private List<PsLine> ps(ExecutionPlan plan, Duration timeout, String... services) {
        List<String> flags = new ArrayList<>(List.of("--all", "--format", "json"));
        flags.addAll(List.of(services));
        List<PsLine> containers = new ArrayList<>();
        String output = compose(plan, timeout, WITH_LOAD, "ps", flags.toArray(String[]::new));
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                containers.add(jsonMapper.readValue(line, PsLine.class));
            }
        }
        return containers;
    }

    @Override
    public void down(ExecutionPlan plan) {
        Map<String, String> labels = plan.labels();
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

    // -p wins over COMPOSE_PROJECT_NAME, so the plan's name is the project. Every command gets the passwords:
    // Compose reads the whole file each time and warns about a variable it cannot find.
    // docker compose (up/down) write the command --> execute
    private String compose(ExecutionPlan plan, Duration timeout, List<String> profiles, String verb,
            String... flags) {
        return compose(plan, timeout, NEVER, profiles, verb, flags);
    }

    private String compose(ExecutionPlan plan, Duration timeout, BooleanSupplier cancelled,
            List<String> profiles, String verb, String... flags) {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "--ansi", "never",
                "-p", plan.project(), "-f", "-"));
        command.addAll(profiles);
        command.add(verb);
        command.addAll(List.of(flags));
        return execute("docker compose " + verb, command, jsonMapper.writeValueAsString(render(plan)),
                credentials.environment(plan), timeout, cancelled);
    }

    // The plan as a Compose file. What every service shares lives here, not in the plan: the execution
    // network and labels, no privileges, a read-only root with /tmp in memory (ADR 0030).
    static Map<String, Object> render(ExecutionPlan plan) {
        Map<String, Object> services = new LinkedHashMap<>();
        for (Node node : plan.nodes()) {
            Map<String, String> labels = new LinkedHashMap<>(plan.labels());
            labels.put(PlanCompiler.COMPONENT_LABEL, node.key().toString());
            Map<String, Object> service = new LinkedHashMap<>();
            Container container = node.container();
            service.put("image", container.image());
            command(service, container.command());
            if (container.user() != null) {
                service.put("user", container.user().uid() + ":" + container.user().gid());
            }
            Wiring wiring = node.wiring();
            Map<String, Object> environment = new LinkedHashMap<>(wiring.environment());
            wiring.secrets().forEach((name, owner) -> environment.put(name, new Secret(owner)));
            service.put("environment", environment);
            service.put("healthcheck", healthcheck(node.health()));
            if (!wiring.dependencies().isEmpty()) {
                Map<String, Object> dependsOn = new LinkedHashMap<>();
                wiring.dependencies().forEach(
                        target -> dependsOn.put(plan.node(target).host(), Map.of("condition", "service_healthy")));
                service.put("depends_on", dependsOn);
            }
            services.put(node.host(),
                    confined(service, labels, node.limits(), tmpfs(container.storage(), container.user())));
        }
        Load load = plan.load();
        if (load != null) {
            Map<String, Object> service = new LinkedHashMap<>();
            service.put("image", load.image());
            command(service, load.command());
            service.put("environment", load.environment());
            service.put("profiles", List.of(LOAD_PROFILE));
            service.put("depends_on",
                    Map.of(plan.node(load.target()).host(), Map.of("condition", "service_healthy")));
            services.put(LOAD_SERVICE, confined(service, plan.labels(), load.limits(), tmpfs(List.of(), null)));
        }
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", plan.project());
        file.put("networks", Map.of(NETWORK, Map.of("labels", plan.labels())));
        file.put("services", services);
        @SuppressWarnings("unchecked")
        Map<String, Object> literal = (Map<String, Object>) literal(file);
        return literal;
    }

    // /tmp for every service, plus the node's own storage owned by the user it runs as. Only that user may
    // read it.
    private static List<String> tmpfs(List<String> storage, RunAs user) {
        List<String> tmpfs = new ArrayList<>(List.of("/tmp"));
        for (String path : storage) {
            tmpfs.add("%s:uid=%d,gid=%d,mode=0700".formatted(path, user.uid(), user.gid()));
        }
        return tmpfs;
    }

    private static void command(Map<String, Object> service, List<String> command) {
        if (command != null) {
            service.put("command", command);
        }
    }

    // An HTTP check runs the image's busybox wget, since Compose has no HTTP check of its own. A command check
    // runs the image's own tool, such as pg_isready.
    private static Map<String, Object> healthcheck(Probe probe) {
        List<String> test = new ArrayList<>(List.of("CMD"));
        switch (probe) {
            case HttpProbe http ->
                test.addAll(List.of("wget", "-qO-", "http://127.0.0.1:" + http.port() + http.path()));
            case CommandProbe command -> test.addAll(command.command());
        }
        Map<String, Object> healthcheck = new LinkedHashMap<>();
        healthcheck.put("test", test);
        healthcheck.put("interval", probe.intervalSeconds() + "s");
        healthcheck.put("timeout", probe.timeoutSeconds() + "s");
        healthcheck.put("retries", probe.retries());
        healthcheck.put("start_period", probe.startPeriodSeconds() + "s");
        return healthcheck;
    }

    private static Map<String, Object> confined(Map<String, Object> service, Map<String, String> labels,
            Limits limits, List<String> tmpfs) {
        service.put("networks", List.of(NETWORK));
        service.put("labels", labels);
        service.put("read_only", true);
        service.put("tmpfs", tmpfs);
        service.put("cap_drop", List.of("ALL"));
        service.put("security_opt", List.of("no-new-privileges:true"));
        service.put("deploy", Map.of("resources", Map.of("limits", Map.of(
                "cpus", BigDecimal.valueOf(limits.cpus()).stripTrailingZeros().toPlainString(),
                "memory", (limits.memoryBytes() >> 20) + "M",
                "pids", limits.pids()))));
        return service;
    }

    // Compose service name -> node key.
    private static Map<String, UUID> nodesByService(ExecutionPlan plan) {
        Map<String, UUID> nodes = new HashMap<>();
        for (Node node : plan.nodes()) {
            nodes.put(node.host(), node.key());
        }
        return nodes;
    }

    // Compose replaces ${NAME} in every value with the backend's own environment, which holds secrets.
    // The plan is literal text, part of it typed by the user, so each $ goes as $$: Compose reads one $.
    // Only a node's password is a real ${NAME}, which Compose fills from what this process passes it.
    private static Object literal(Object value) {
        return switch (value) {
            case Secret(UUID owner) -> "${" + ExecutionCredentials.variable(owner) + "}";
            case String text -> text.replace("$", "$$");
            case Map<?, ?> map -> {
                Map<Object, Object> copy = new LinkedHashMap<>();
                map.forEach((key, entry) -> copy.put(key, literal(entry)));
                yield copy;
            }
            case List<?> list -> list.stream().map(ComposeRuntimeEngine::literal).toList();
            case null, default -> value;
        };
    }

    private void backendNetwork(ExecutionPlan plan, String... action) {
        List<String> arguments = new ArrayList<>(List.of("network"));
        arguments.addAll(List.of(action));
        arguments.add(network(plan));
        arguments.add(backendContainer);
        docker(arguments);
    }

    // Compose names each network after the project: <project>_<network>.
    private static String network(ExecutionPlan plan) {
        return plan.project() + "_" + NETWORK;
    }

    // only docker comands not compose
    private static String docker(List<String> arguments) {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(arguments);
        return execute("docker " + arguments.get(0) + " " + arguments.get(1), command, null, Map.of(), STOP_TIMEOUT,
                NEVER);
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
    private static String execute(String step, List<String> command, String input, Map<String, String> environment,
            Duration timeout, BooleanSupplier cancelled) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().putAll(environment);
            Process process = builder.start();
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
    private record PsLine(@JsonProperty("Name") String name, @JsonProperty("Service") String service,
            @JsonProperty("State") String state, @JsonProperty("Health") String health,
            @JsonProperty("ExitCode") Integer exitCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StatsLine(@JsonProperty("Name") String name, @JsonProperty("CPUPerc") String cpu,
            @JsonProperty("MemUsage") String memory) {
    }

    private static String tail(String text) {
        String trimmed = text.strip();
        return trimmed.length() > MAX_OUTPUT_IN_REASON
                ? trimmed.substring(trimmed.length() - MAX_OUTPUT_IN_REASON)
                : trimmed;
    }
}
