package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Container;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.HttpProbe;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Limits;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Load;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Wiring;
import es.codeurjc.infracture.execution.domain.LoadSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

// Runs the real docker CLI against the Docker engine that Testcontainers also uses. A busybox node that
// serves its health page with httpd stands in for the demo image, which CI does not build.
class ComposeRuntimeEngineIntegrationTests {

    private final String instance = "test-" + UUID.randomUUID().toString().replace("-", "");
    private final Map<String, String> labels =
            Map.of(PlanCompiler.INSTANCE_LABEL, instance, PlanCompiler.EXECUTION_LABEL, "1");
    private final ComposeRuntimeEngine engine = new ComposeRuntimeEngine(JsonMapper.builder().build(),
            new ExecutionCredentials(), "");
    private static final UUID PROBE = UUID.fromString("0b6c1f4e-8a2d-4e7b-9c3f-5d1a2b3c4d5e");
    private static final Limits LIMITS = new Limits(0.5, 384L << 20, 200);
    private static final LoadSummary SUMMARY = new LoadSummary(3L, 0L, 0L, 1.5, 2.0);
    private static final BooleanSupplier NEVER = () -> false;

    @AfterEach
    void removeEverything() {
        engine.down(plan());
    }

    @Test
    void startsAHealthyPlanAndLeavesNothingAfterDown() {
        engine.up(plan(), NEVER);

        assertThat(docker("container", "ls", "-q", "--filter", "health=healthy", "--filter", label())).hasSize(1);
        assertThat(docker("network", "ls", "-q", "--filter", label())).hasSize(1);

        engine.down(plan());

        assertThat(labelled()).isEmpty();
    }

    @Test
    void reportsTheStatusOfEachService() {
        engine.up(plan(), NEVER);
        assertThat(engine.status(plan())).isEqualTo(new RuntimeStatus(Map.of(PROBE, ComponentStatus.HEALTHY), null));

        docker("container", "stop", "-t", "1", "infracture-" + instance + "-exec-1-probe-1");

        assertThat(engine.status(plan())).isEqualTo(new RuntimeStatus(Map.of(PROBE, ComponentStatus.EXITED), null));
    }

    @Test
    void readsTheRealUsageOfARunningServiceAndNothingOnceItStops() {
        engine.up(plan(), NEVER);

        ResourceUsage usage = engine.usage(plan()).get(PROBE);

        assertThat(usage.cpuPercent()).isNotNull().isGreaterThanOrEqualTo(0.0);
        assertThat(usage.memoryBytes()).isNotNull().isPositive();

        docker("container", "stop", "-t", "1", "infracture-" + instance + "-exec-1-probe-1");

        assertThat(engine.usage(plan())).isEmpty();
    }

    // Compose would replace ${HOME} with the value from the backend's own environment, and reject "$5".
    @Test
    void passesTextWithDollarSignsToTheContainerAsWritten() {
        ExecutionPlan plan = plan(SERVE, Map.of("COMPONENT_NAME", "${HOME} costs $5"));

        engine.up(plan, NEVER);

        assertThat(docker("container", "inspect", "-f", "{{range .Config.Env}}{{println .}}{{end}}",
                "infracture-" + instance + "-exec-1-probe-1")).contains("COMPONENT_NAME=${HOME} costs $5");
        assertThat(engine.status(plan).nodes()).containsExactly(Map.entry(PROBE, ComponentStatus.HEALTHY));
    }

    @Test
    void readsWhatEachServicePrintedWithItsTimeAndFromAGivenTime() throws InterruptedException {
        ExecutionPlan plan = plan("echo first line; echo 'second | line'; " + SERVE, Map.of());
        engine.up(plan, NEVER);
        List<LogLine> lines = engine.logs(plan, null);
        for (int attempt = 0; attempt < 20 && lines.size() < 2; attempt++) {
            Thread.sleep(250);
            lines = engine.logs(plan, null);
        }

        assertThat(lines).extracting(LogLine::node, LogLine::text)
                .containsExactly(tuple(PROBE, "first line"), tuple(PROBE, "second | line"));
        assertThat(lines.getFirst().at()).isBeforeOrEqualTo(lines.getLast().at())
                .isAfter(Instant.now().minusSeconds(60));
        assertThat(engine.logs(plan, lines.getLast().at().plusNanos(1))).isEmpty();
    }

    @Test
    void joinsTheBackendToTheNetworkAndLeavesItBeforeCleanup() {
        String backend = "infracture-" + instance + "-backend";
        // Named like the platform's network, which sorts after the experiment's: Docker would give the
        // default route to the experiment's network unless the backend joins it with a lower priority.
        String platform = "infracture-" + instance + "_default";
        docker("network", "create", platform);
        docker("container", "run", "-d", "--quiet", "--name", backend, "--network", platform, "busybox:1.37",
                "sleep", "300");
        ComposeRuntimeEngine inContainer = new ComposeRuntimeEngine(JsonMapper.builder().build(),
                new ExecutionCredentials(), backend);
        try {
            String defaultRoute = defaultRoute(backend);
            inContainer.up(plan(), NEVER);
            assertThat(docker("container", "inspect", "-f", "{{range $name, $n := .NetworkSettings.Networks}}"
                    + "{{$name}} {{end}}", backend).getFirst())
                    .contains("infracture-" + instance + "-exec-1_exec");
            // A new default route would leave the backend's open connections, such as event streams,
            // without their replies on Linux.
            assertThat(defaultRoute(backend)).isEqualTo(defaultRoute);

            inContainer.down(plan());

            assertThat(labelled()).isEmpty();
        } finally {
            docker("container", "rm", "-f", backend);
            docker("network", "rm", platform);
        }
    }

    private static String defaultRoute(String container) {
        return docker("exec", container, "ip", "route").stream()
                .filter(route -> route.startsWith("default")).findFirst().orElseThrow();
    }

    @Test
    void runsATemporaryPostgresqlInMemoryThatItsConsumerReachesWithTheSamePassword() {
        ExecutionPlan plan = databasePlan();

        engine.up(plan, NEVER);

        String database = "infracture-" + instance + "-exec-1-c-" + PlanCompilerTests.DATABASE + "-1";
        String consumer = "infracture-" + instance + "-exec-1-c-" + plan.nodes().getFirst().key() + "-1";
        String password = environment(consumer, "DATABASE_PASSWORD");
        assertThat(password).matches("[A-Za-z0-9_-]{43}");
        assertThat(environment(database, "POSTGRES_PASSWORD")).isEqualTo(password);
        assertThat(docker("exec", "-e", "PGPASSWORD=" + password, database, "psql", "-h", "127.0.0.1", "-U", "app",
                "-d", "app", "-tAc", "select current_user")).containsExactly("app");
        assertThat(docker("container", "inspect", "-f", "{{.Config.User}} {{len .Mounts}}", database))
                .containsExactly("70:70 0");
        assertThat(JsonMapper.builder().build().writeValueAsString(plan)).doesNotContain(password);
        assertThat(engine.status(plan).nodes()).containsOnly(
                Map.entry(PlanCompilerTests.DATABASE, ComponentStatus.HEALTHY),
                Map.entry(plan.nodes().getFirst().key(), ComponentStatus.HEALTHY));

        engine.down(plan);

        assertThat(labelled()).isEmpty();
    }

    private static String environment(String container, String name) {
        return docker("container", "inspect", "-f", "{{range .Config.Env}}{{println .}}{{end}}", container).stream()
                .filter(variable -> variable.startsWith(name + "=")).map(variable -> variable.substring(
                        name.length() + 1))
                .findFirst().orElseThrow();
    }

    private ExecutionPlan databasePlan() {
        ExecutionPlan compiled = new PlanCompiler(List.of(new HttpServiceDriver("unused"),
                new PostgresqlDriver("postgres:18.7-alpine")), instance, "unused")
                .compile(PlanCompilerTests.databaseExecution(1L));
        Node orders = compiled.nodes().getFirst();
        Node consumer = new Node(orders.key(), orders.host(),
                new Container("busybox:1.37", List.of("sh", "-c", SERVE), null, List.of()), orders.wiring(),
                new HttpProbe(8080, "/health", 1, 2, 1, 10), null, null, LIMITS);
        return new ExecutionPlan(plan().project(), labels, List.of(consumer, compiled.nodes().getLast()), null);
    }

    @Test
    void startsTheLoadOnlyWhenAskedAndKeepsItsSummaryWhenStopped() {
        ExecutionPlan plan = withLoad("while true; do sleep 1; done");
        engine.up(plan, NEVER);
        assertThat(engine.status(plan)).isEqualTo(new RuntimeStatus(Map.of(PROBE, ComponentStatus.HEALTHY), null));
        assertThat(engine.stopLoad(plan)).isEqualTo(new LoadReport(null, null));

        engine.startLoad(plan, NEVER);
        assertThat(engine.status(plan).load()).isEqualTo(ComponentStatus.HEALTHY);

        LoadReport report = engine.stopLoad(plan);
        assertThat(report).isEqualTo(new LoadReport(105, SUMMARY));
        assertThat(report.completed()).isFalse();
        assertThat(engine.stopLoad(plan)).isEqualTo(report);

        engine.down(plan);
        assertThat(labelled()).isEmpty();
    }

    @Test
    void readsTheSummaryOfALoadThatEndedOnItsOwn() throws InterruptedException {
        ExecutionPlan plan = withLoad("true");
        engine.up(plan, NEVER);
        engine.startLoad(plan, NEVER);
        while (engine.status(plan).load() != ComponentStatus.EXITED) {
            Thread.sleep(200);
        }

        assertThat(engine.stopLoad(plan)).isEqualTo(new LoadReport(0, SUMMARY));
    }

    @Test
    void reportsAnUnhealthyServiceAndCleansUpWhatItCreated() {
        assertThatThrownBy(() -> engine.up(unhealthy(), NEVER))
                .isInstanceOf(RuntimeEngineException.class)
                .hasMessageStartingWith("docker compose up failed:")
                .hasMessageContaining("unhealthy");

        engine.down(unhealthy());

        assertThat(labelled()).isEmpty();
    }

    // The first health check runs only after its interval, so the start waits until Stop cancels it.
    @Test
    void cancelsAStartThatWaitsForHealthAndLeavesNothingAfterDown() {
        ExecutionPlan slow = plan(SERVE, Map.of(), new HttpProbe(8080, "/health", 300, 2, 1, 10));
        Instant stopAt = Instant.now().plusSeconds(2);

        assertThatThrownBy(() -> engine.up(slow, () -> Instant.now().isAfter(stopAt)))
                .isInstanceOf(StartCancelledException.class)
                .hasMessage("docker compose up was cancelled because the execution is stopping");
        assertThat(Duration.between(stopAt, Instant.now())).isLessThan(Duration.ofSeconds(30));

        engine.down(slow);

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
            assertThatThrownBy(() -> engine.down(plan()))
                    .isInstanceOf(RuntimeEngineException.class)
                    .hasMessageStartingWith("docker volume rm failed:");
            assertThat(docker("container", "ls", "-q", "--no-trunc", "--filter", "id=" + other)).hasSize(1);
        } finally {
            docker("container", "rm", "-f", other);
        }

        engine.down(plan());

        assertThat(labelled()).isEmpty();
    }

    @Test
    void removesLabelledContainersThatComposeDoesNotKnow() {
        docker("container", "run", "-d", "--quiet", "--label", label(PlanCompiler.INSTANCE_LABEL),
                "--label", label(PlanCompiler.EXECUTION_LABEL), "busybox:1.37", "sleep", "300");

        engine.down(plan());

        assertThat(labelled()).isEmpty();
    }

    @Test
    void leavesContainersOfOtherExecutionsAlone() {
        String other = docker("container", "run", "-d", "--quiet", "--label", label(PlanCompiler.INSTANCE_LABEL),
                "--label", PlanCompiler.EXECUTION_LABEL + "=2", "busybox:1.37", "sleep", "300").getLast();
        try {
            engine.down(plan());

            assertThat(docker("container", "ls", "-aq", "--no-trunc", "--filter", "id=" + other)).hasSize(1);
        } finally {
            docker("container", "rm", "-f", other);
        }
    }

    // Serves /health from /tmp, the only writable place, and ends at once on SIGTERM so down does not wait.
    private static final String SERVE = "mkdir -p /tmp/www && echo ok > /tmp/www/health && httpd -p 8080 -h /tmp/www"
            + " && trap 'exit 0' TERM && while true; do sleep 1; done";

    private ExecutionPlan plan() {
        return plan(SERVE, Map.of());
    }

    // The probe asks for a page httpd does not have, so the node never becomes healthy.
    private ExecutionPlan unhealthy() {
        return plan(SERVE, Map.of(), new HttpProbe(8080, "/missing", 1, 2, 1, 0));
    }

    private ExecutionPlan plan(String script, Map<String, String> environment) {
        return plan(script, environment, new HttpProbe(8080, "/health", 1, 2, 1, 10));
    }

    private ExecutionPlan plan(String script, Map<String, String> environment, HttpProbe health) {
        Node probe =
                new Node(PROBE, "probe", new Container("busybox:1.37", List.of("sh", "-c", script), null, List.of()),
                        new Wiring(environment, Map.of(), List.of()), health, null, null, LIMITS);
        return new ExecutionPlan("infracture-" + instance + "-exec-1", labels, List.of(probe), null);
    }

    // A shell stands in for k6: it prints the summary line when it ends, as the load scripts do.
    private ExecutionPlan withLoad(String body) {
        String summary = ComposeRuntimeEngine.SUMMARY_MARKER + "{\"requests\":3,\"failedRequests\":0,"
                + "\"droppedIterations\":0,\"averageLatencyMs\":1.5,\"p95LatencyMs\":2.0}";
        Load load = new Load("busybox:1.37", List.of("sh", "-c", "finish() { echo '" + summary + "'; }; "
                + "trap 'finish; exit 105' TERM; " + body + "; finish"), Map.of(), PROBE, LIMITS);
        ExecutionPlan plan = plan();
        return new ExecutionPlan(plan.project(), plan.labels(), plan.nodes(), load);
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
