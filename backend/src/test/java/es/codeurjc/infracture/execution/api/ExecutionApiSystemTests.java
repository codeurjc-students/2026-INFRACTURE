package es.codeurjc.infracture.execution.api;

import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.execution.application.ComponentStatus;
import es.codeurjc.infracture.execution.application.ExecutionReconciler;
import es.codeurjc.infracture.execution.application.LoadReport;
import es.codeurjc.infracture.execution.application.LogLine;
import es.codeurjc.infracture.execution.application.RuntimeEngine;
import es.codeurjc.infracture.execution.application.RuntimeEngineException;
import es.codeurjc.infracture.execution.application.RuntimeStatus;
import es.codeurjc.infracture.execution.application.ServiceProbe;
import es.codeurjc.infracture.execution.application.StartCancelledException;
import es.codeurjc.infracture.execution.domain.LoadSummary;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.project.api.ApiSession;
import es.codeurjc.infracture.project.persistence.ProjectRepository;
import es.codeurjc.infracture.project.persistence.ScenarioRepository;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:admin-test.properties", properties = {
        "infracture.execution.reconciler.enabled=false",
        "infracture.execution.cleanup-retry-pause=0s",
        "infracture.execution.events.poll-interval=1h" })
@Testcontainers
class ExecutionApiSystemTests {

    static final String ORDERS = "3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10";
    static final UUID ORDERS_KEY = UUID.fromString(ORDERS);
    static final RuntimeStatus ORDERS_HEALTHY = new RuntimeStatus(Map.of(ORDERS_KEY, ComponentStatus.HEALTHY), null);
    private static final String CACHE = "0b9c8d7e-6f5a-4b3c-8d2e-1f0a9b8c7d6e";
    private static final String DATABASE = "7c2e4a6b-8d0f-4b1c-9e3a-5f7b9d1c3e5a";
    static final String ANOTHER_RUNNING = "Another experiment is running. Run this scenario when it ends.";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private ExecutionRepository executions;

    @Autowired
    private ScenarioRepository scenarios;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private ExecutionReconciler reconciler;

    // Docker is exercised by ComposeRuntimeEngineIntegrationTests; here the test drives each tick by hand.
    @MockitoBean
    private RuntimeEngine engine;

    @MockitoBean
    private ServiceProbe probe;

    @BeforeEach
    void clearData() {
        when(engine.status(any())).thenReturn(ORDERS_HEALTHY);
        executions.deleteAllInBatch();
        scenarios.deleteAllInBatch();
        projects.deleteAllInBatch();
    }

    @Test
    void reservesTheSavedRevisionAndKeepsOnlyThatScenarioReadOnly() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        String other = createScenario(alice);

        long id = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .body("phase", equalTo("RESERVED"))
                .body("scenarioRevision", equalTo(2))
                .body("stopCause", nullValue())
                .body("outcome", nullValue())
                .body("plan.nodes[0].key", equalTo(ORDERS))
                .body("plan.nodes[0].container.image", equalTo("infracture/demo-app:0.1.0"))
                .body("plan.nodes[0].wiring.environment.COMPONENT_NAME", equalTo("orders-api"))
                .extract().jsonPath().getLong("id");

        alice.read().get(scenario + "/executions/current").then().statusCode(200)
                .body("id", equalTo((int) id)).body("phase", equalTo("RESERVED"))
                .body("plan.project", matchesPattern("infracture-test-[0-9a-f]{32}-exec-" + id))
                .body("plan.labels['io.infracture.instance']", startsWith("test-"));
        String readOnly = "The scenario is read-only while an experiment uses it. Stop it to edit the scenario.";
        alice.write().body(graph(2, "")).put(scenario + "/graph").then().spec(problem(409, readOnly));
        alice.write().body("{\"name\":\"Renamed\"}").put(scenario).then().spec(problem(409, readOnly));
        alice.read().get(scenario + "/graph").then().body("revision", equalTo(2));
        alice.write().body(graph(1, http(ORDERS))).put(other + "/graph").then().statusCode(200);
    }

    @Test
    void freezesTheLoadAndPlansK6ApartFromTheNodes() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = createScenario(alice);
        alice.write().body("""
                {"revision":1,"components":[%s],"connections":[],
                 "workload":{"targetKey":"%s","profile":"CONSTANT_HTTP",
                  "parameters":{"requestsPerSecond":10,"virtualUsers":5,"durationSeconds":60},"seed":42}}
                """.formatted(http(ORDERS), ORDERS)).put(scenario + "/graph").then().statusCode(200);

        alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .body("workload.targetKey", equalTo(ORDERS))
                .body("workload.profile", equalTo("CONSTANT_HTTP"))
                .body("workload.parameters.requestsPerSecond", equalTo(10))
                .body("workload.seed", equalTo(42))
                .body("plan.load.image", equalTo("infracture/load-generator:0.1.0"))
                .body("plan.load.target", equalTo(ORDERS))
                .body("plan.load.environment.TARGET_URL", equalTo("http://c-" + ORDERS + ":8080/"))
                .body("plan.load.environment.REQUESTS_PER_SECOND", equalTo("10"));
        alice.read().get(scenario + "/executions/current").then().body("workload.seed", equalTo(42))
                .body("workload.summaryCollectedAt", nullValue()).body("workload.summary", nullValue());
    }

    @Test
    void stopsTheLoadFirstAndShowsItsSummaryAfterStop() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = createScenario(alice);
        alice.write().body("""
                {"revision":1,"components":[%s],"connections":[],
                 "workload":{"targetKey":"%s","profile":"CONSTANT_HTTP",
                  "parameters":{"requestsPerSecond":10,"virtualUsers":5,"durationSeconds":60},"seed":42}}
                """.formatted(http(ORDERS), ORDERS)).put(scenario + "/graph").then().statusCode(200);
        long id = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");
        when(engine.stopLoad(any())).thenReturn(new LoadReport(105, new LoadSummary(120L, 1L, 0L, 52.5, null)));
        reconciler.reconcile();
        reconciler.reconcile();

        alice.write().post(scenario + "/executions/" + id + "/stop").then().statusCode(202);
        reconciler.reconcile();

        InOrder order = inOrder(engine);
        order.verify(engine).up(any(), any());
        order.verify(engine).startLoad(any(), any());
        order.verify(engine).stopLoad(any());
        order.verify(engine).down(any());
        alice.read().get(scenario + "/executions/current").then()
                .body("phase", equalTo("RELEASED"))
                .body("workload.completed", equalTo(false))
                .body("workload.summaryCollectedAt", notNullValue())
                .body("workload.summary.requests", equalTo(120))
                .body("workload.summary.failedRequests", equalTo(1))
                .body("workload.summary.averageLatencyMs", equalTo(52.5f))
                .body("workload.summary.p95LatencyMs", nullValue());
    }

    @Test
    void stopsTheRunningExecutionAndUnlocksTheScenarioOnceCleanupEnds() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        long id = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");
        String stop = scenario + "/executions/" + id + "/stop";

        when(probe.get(any())).thenReturn("{\"component\":\"orders-api\"}");
        alice.read().get(scenario + "/executions/current").then()
                .body("components[0].name", equalTo("orders-api")).body("components[0].status", nullValue());
        reconciler.reconcile();
        reconciler.reconcile();
        alice.read().get(scenario + "/executions/current").then().body("phase", equalTo("RUNNING"))
                .body("components[0].key", equalTo(ORDERS))
                .body("components[0].status", equalTo("HEALTHY"))
                .body("components[0].response", equalTo("{\"component\":\"orders-api\"}"));
        alice.write().post(stop).then().statusCode(202)
                .body("phase", equalTo("RUNNING")).body("stopCause", equalTo("STOP_REQUESTED"));
        alice.write().body(graph(2, "")).put(scenario + "/graph").then().statusCode(409);

        reconciler.reconcile();
        alice.read().get(scenario + "/executions/current").then()
                .body("phase", equalTo("RELEASED")).body("outcome", equalTo("COMPLETED"));
        alice.write().post(stop).then().statusCode(202).body("outcome", equalTo("COMPLETED"));
        alice.write().body(graph(2, http(ORDERS))).put(scenario + "/graph").then().statusCode(200);
        verify(engine).up(any(), any());
        verify(engine).down(argThat(plan -> plan.labels().equals(Map.of("io.infracture.instance",
                instanceOf(alice, scenario), "io.infracture.execution", String.valueOf(id)))));
    }

    @Test
    void reportsAFailedStartAndFreesTheInstanceAfterCleanup() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        doThrow(new RuntimeEngineException("docker compose up failed: container is unhealthy"))
                .when(engine).up(any(), any());
        alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202);

        reconciler.reconcile();
        reconciler.reconcile();

        alice.read().get(scenario + "/executions/current").then()
                .body("phase", equalTo("RELEASED")).body("outcome", equalTo("FAILED"))
                .body("failureReason", equalTo("docker compose up failed: container is unhealthy"));
        alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .body("phase", equalTo("RESERVED"));
    }

    // The engine asks whether to go on while it waits; a Stop sent meanwhile cancels the start.
    @Test
    void cancelsTheStartWhenStopArrivesBeforeTheNodesAreHealthy() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        long id = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");
        String stop = scenario + "/executions/" + id + "/stop";
        doAnswer(invocation -> {
            alice.write().post(stop).then().statusCode(202).body("phase", equalTo("STARTING"));
            alice.write().post(stop).then().statusCode(202).body("stopCause", equalTo("STOP_REQUESTED"));
            BooleanSupplier cancelled = invocation.getArgument(1);
            assertThat(cancelled.getAsBoolean()).isTrue();
            throw new StartCancelledException("docker compose up was cancelled because the execution is stopping");
        }).when(engine).up(any(), any());

        reconciler.reconcile();
        alice.read().get(scenario + "/executions/current").then()
                .body("phase", equalTo("STARTING")).body("failureReason", nullValue());
        reconciler.reconcile();

        alice.read().get(scenario + "/executions/current").then()
                .body("phase", equalTo("RELEASED")).body("outcome", equalTo("COMPLETED"))
                .body("failureReason", nullValue());
        verify(engine).down(any());
        alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202);
    }

    // After the automatic attempts, the cleanup waits for Stop. Until it succeeds, the reservation and the
    // read-only scenario stay, and the outcome stays the one the stop cause says.
    @Test
    void keepsTheScenarioLockedUntilAFailedCleanupIsRetriedWithStop() {
        ApiSession alice = ApiSession.signUp(port);
        ApiSession bob = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        String other = statelessScenario(bob);
        long id = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");
        String stop = scenario + "/executions/" + id + "/stop";
        reconciler.reconcile();
        doThrow(new RuntimeEngineException("docker volume rm failed: volume is in use"))
                .doThrow(new RuntimeEngineException("docker volume rm failed: volume is in use"))
                .doThrow(new RuntimeEngineException("docker volume rm failed: volume is in use"))
                .doNothing()
                .when(engine).down(any());
        alice.write().post(stop).then().statusCode(202);

        for (int tick = 0; tick < 4; tick++) {
            reconciler.reconcile();
        }

        alice.read().get(scenario + "/executions/current").then()
                .body("phase", equalTo("STOPPING")).body("outcome", nullValue())
                .body("stopCause", equalTo("STOP_REQUESTED"))
                .body("cleanupFailures", equalTo(3)).body("cleanupRetryNeeded", equalTo(true))
                .body("cleanupError", equalTo("docker volume rm failed: volume is in use"));
        alice.write().body(graph(2, "")).put(scenario + "/graph").then().statusCode(409);
        bob.write().body(run(2)).post(other + "/executions").then().spec(problem(409, ANOTHER_RUNNING));

        alice.write().post(stop).then().statusCode(202)
                .body("cleanupRetryNeeded", equalTo(false)).body("cleanupFailures", equalTo(0));
        reconciler.reconcile();

        alice.read().get(scenario + "/executions/current").then()
                .body("phase", equalTo("RELEASED")).body("outcome", equalTo("COMPLETED"))
                .body("failureReason", nullValue());
        bob.write().body(run(2)).post(other + "/executions").then().statusCode(202);
    }

    @Test
    void streamsTheExecutionEventsToItsOwnerAndResumesAfterACursor() {
        ApiSession alice = ApiSession.signUp(port);
        ApiSession bob = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        long id = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");
        String events = scenario + "/executions/" + id + "/events";
        when(engine.logs(any(), any())).thenReturn(List.of(new LogLine(ORDERS_KEY,
                Instant.parse("2026-11-21T10:00:00Z"), "connected with password=hunter2")));
        reconciler.reconcile();
        reconciler.reconcile();
        alice.write().post(scenario + "/executions/" + id + "/stop").then().statusCode(202);
        reconciler.reconcile();

        String all = alice.read().accept("text/event-stream").get(events).then().statusCode(200)
                .contentType(startsWith("text/event-stream")).extract().asString();
        List<String> sent = messages(all);
        assertThat(sent).extracting(message -> message.replaceAll("^id:\\d+\\n", "").lines().findFirst()
                .orElseThrow()).containsExactly("event:event", "event:event", "event:event", "event:state",
                        "event:log", "event:event", "event:event", "event:event");
        assertThat(all).contains("\"phase\":\"RESERVED\"", "\"cause\":\"STOP_REQUESTED\"",
                "\"phase\":\"RELEASED\"", "\"status\":\"HEALTHY\"",
                "\"line\":\"connected with password=[redacted]\"", "\"componentKey\":\"" + ORDERS + "\"")
                .doesNotContain("hunter2");

        String cursor = sent.get(4).lines().findFirst().orElseThrow().substring("id:".length());
        String rest = alice.read().accept("text/event-stream").header("Last-Event-ID", cursor).get(events)
                .then().statusCode(200).extract().asString();
        assertThat(messages(rest)).hasSize(3).allSatisfy(message -> assertThat(message).contains("event:event"));
        assertThat(alice.read().accept("text/event-stream").queryParam("after", 999_999).get(events).then()
                .statusCode(200).extract().asString()).startsWith("event:reset").contains("\"phase\":\"RELEASED\"");

        bob.read().accept("text/event-stream").get(events).then().statusCode(404);
        given().port(port).accept("text/event-stream").get(events).then().statusCode(401);
    }

    // Nothing polls here (poll interval 1h, heartbeat 15s): only the database notice can wake the stream.
    @Test
    void sendsANewEventToAnOpenStreamAsSoonAsItCommits() throws Exception {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        long id = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + scenario + "/executions/" + id + "/events"))
                .header("Accept", "text/event-stream").header("Cookie", alice.cookieHeader()).build();

        try (HttpClient client = HttpClient.newHttpClient();
                Stream<String> body = client.send(request, BodyHandlers.ofLines()).body()) {
            Iterator<String> lines = body.iterator();
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> readUntil(lines, "\"phase\":\"RESERVED\""));
            alice.write().post(scenario + "/executions/" + id + "/stop").then().statusCode(202);

            assertTimeoutPreemptively(Duration.ofSeconds(10),
                    () -> readUntil(lines, "\"cause\":\"STOP_REQUESTED\""));
        }
    }

    @Test
    void endsAnOpenStreamWhenThePasswordChangeRevokesItsSession() throws Exception {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        long id = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + scenario + "/executions/" + id + "/events"))
                .header("Accept", "text/event-stream").header("Cookie", alice.cookieHeader()).build();

        try (HttpClient client = HttpClient.newHttpClient();
                Stream<String> body = client.send(request, BodyHandlers.ofLines()).body()) {
            Iterator<String> lines = body.iterator();
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> readUntil(lines, "\"phase\":\"RESERVED\""));
            alice.write().body("""
                    {"currentPassword":"A secure integration password 2026!",
                     "newPassword":"Another secure password 2027!"}
                    """).put("/api/v1/users/me/password").then().statusCode(200);
            // The stop event wakes the stream, whose next read finds the session revoked.
            alice.write().post(scenario + "/executions/" + id + "/stop").then().statusCode(202);

            List<String> rest = new ArrayList<>();
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> lines.forEachRemaining(rest::add));
            assertThat(rest).noneMatch(line -> line.contains("STOP_REQUESTED"));
        }
    }

    @Test
    void hidesStopFromOtherUsers() {
        ApiSession alice = ApiSession.signUp(port);
        ApiSession bob = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);
        long id = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");

        bob.write().post(scenario + "/executions/" + id + "/stop").then().statusCode(404);
        alice.read().get(scenario + "/executions/current").then().body("stopCause", nullValue());
    }

    @Test
    void returnsTheSameExecutionWhenRunIsRepeated() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = statelessScenario(alice);

        long first = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");
        long second = alice.write().body(run(2)).post(scenario + "/executions").then().statusCode(202)
                .extract().jsonPath().getLong("id");

        assertThat(second).isEqualTo(first);
        assertThat(executions.count()).isEqualTo(1);
    }

    @Test
    void rejectsARunFromAnotherUserWithoutShowingTheirExecution() {
        ApiSession alice = ApiSession.signUp(port);
        ApiSession bob = ApiSession.signUp(port);
        String aliceScenario = statelessScenario(alice);
        String bobScenario = statelessScenario(bob);
        alice.write().body(run(2)).post(aliceScenario + "/executions").then().statusCode(202);

        bob.write().body(run(2)).post(bobScenario + "/executions").then().spec(problem(409, ANOTHER_RUNNING));
        bob.read().get(bobScenario + "/executions/current").then().statusCode(204);
        bob.read().get(aliceScenario + "/executions/current").then().statusCode(404);
    }

    @Test
    void rejectsAStaleRevisionAndScenariosWithBlockingErrors() {
        ApiSession alice = ApiSession.signUp(port);
        String stateless = statelessScenario(alice);
        String empty = createScenario(alice);
        String redisOnly = createScenario(alice);
        alice.write().body(graph(1, redis(CACHE))).put(redisOnly + "/graph").then().statusCode(200);

        alice.write().body(run(1)).post(stateless + "/executions").then()
                .spec(problem(409, "The scenario was saved after revision 1. Reload it to run revision 2."));
        alice.write().body(run(1)).post(empty + "/executions").then()
                .spec(problem(422, "The scenario has errors that block Run"))
                .body("issues[0].code", equalTo("EMPTY_SCENARIO"));
        alice.write().body(run(2)).post(redisOnly + "/executions").then().statusCode(422)
                .body("issues[0].code", equalTo("UNSUPPORTED_IN_THIS_VERSION"))
                .body("issues[0].target.key", equalTo(CACHE));
        alice.write().body("{}").post(stateless + "/executions").then().statusCode(400);

        assertThat(executions.count()).isZero();
    }

    @Test
    void runsADatabaseCrudServiceOnlyWithItsTemporaryPostgresql() {
        ApiSession alice = ApiSession.signUp(port);
        String alone = createScenario(alice);
        alice.write().body(graph(1, http(ORDERS, "DATABASE_CRUD"), "")).put(alone + "/graph").then()
                .statusCode(200);
        String withDatabase = createScenario(alice);
        alice.write().body(graph(1, http(ORDERS, "DATABASE_CRUD") + "," + postgresql(DATABASE),
                connection(ORDERS, "database", DATABASE))).put(withDatabase + "/graph").then().statusCode(200);

        alice.write().body(run(2)).post(alone + "/executions").then().statusCode(422)
                .body("issues[0].code", equalTo("MISSING_CONNECTION"))
                .body("issues[0].field", equalTo("database"));
        alice.write().body(run(2)).post(withDatabase + "/executions").then().statusCode(202)
                .body("plan.nodes.key", contains(ORDERS, DATABASE))
                .body("plan.nodes[0].wiring.dependencies", contains(DATABASE))
                .body("plan.nodes[0].wiring.environment.DATABASE_URL",
                        equalTo("postgresql://c-" + DATABASE + ":5432/app"))
                .body("plan.nodes[0].wiring.secrets.DATABASE_PASSWORD", equalTo(DATABASE))
                .body("plan.nodes[1].container.image", startsWith("postgres:18.7-alpine@sha256:"))
                .body("plan.nodes[1].container.storage", contains("/var/lib/postgresql", "/var/run/postgresql"))
                .body("plan.nodes[1].wiring.secrets.POSTGRES_PASSWORD", equalTo(DATABASE))
                .body("plan.nodes[1].wiring.environment", not(hasKey("POSTGRES_PASSWORD")));
    }

    @Test
    void reservesOnlyOneOfSimultaneousRunsFromDifferentUsers() throws Exception {
        int users = 4;
        List<RequestSpecification> requests = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (int user = 0; user < users; user++) {
            ApiSession session = ApiSession.signUp(port);
            paths.add(statelessScenario(session) + "/executions");
            requests.add(session.write().body(run(2)));
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(users);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int user = 0; user < users; user++) {
                RequestSpecification request = requests.get(user);
                String path = paths.get(user);
                results.add(executor.submit(() -> {
                    start.await();
                    return given().spec(request).post(path).statusCode();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }

            assertThat(statuses).containsOnly(202, 409).filteredOn(status -> status == 202).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(executions.count()).isEqualTo(1);
    }

    // Save and Run of the same revision lock the same row, so one of them always sees the other:
    // either Run reserves revision 2 and the save is rejected, or the save wins and Run is stale.
    @Test
    void ordersASaveAndARunOfTheSameRevisionSoTheSnapshotIsOneSavedRevision() throws Exception {
        ApiSession alice = ApiSession.signUp(port);
        String renamed = graph(2, http(ORDERS).replace("orders-api", "orders-v2"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                executions.deleteAllInBatch();
                String scenario = statelessScenario(alice);
                // Two tabs of one browser share the session and its CSRF token.
                RequestSpecification sameCsrfToken = alice.write();
                RequestSpecification save = given().spec(sameCsrfToken).body(renamed);
                RequestSpecification reserve = given().spec(sameCsrfToken).body(run(2));
                CountDownLatch start = new CountDownLatch(1);
                Future<Integer> saved = executor.submit(() -> {
                    start.await();
                    return given().spec(save).put(scenario + "/graph").statusCode();
                });
                Future<Integer> ran = executor.submit(() -> {
                    start.await();
                    return given().spec(reserve).post(scenario + "/executions").statusCode();
                });
                start.countDown();

                if (ran.get() == 202) {
                    assertThat(saved.get()).isEqualTo(409);
                    alice.read().get(scenario + "/executions/current").then()
                            .body("scenarioRevision", equalTo(2)).body("components[0].name", equalTo("orders-api"));
                    alice.read().get(scenario + "/graph").then().body("revision", equalTo(2));
                } else {
                    assertThat(ran.get()).isEqualTo(409);
                    assertThat(saved.get()).isEqualTo(200);
                    assertThat(executions.count()).isZero();
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // One entry per SSE message, without the blank line that ends it.
    private static List<String> messages(String stream) {
        return Arrays.stream(stream.split("\\n\\n")).filter(message -> !message.isBlank()).toList();
    }

    private static String instanceOf(ApiSession session, String scenario) {
        return session.read().get(scenario + "/executions/current").then().extract()
                .path("plan.labels['io.infracture.instance']");
    }

    static String statelessScenario(ApiSession session) {
        String scenario = createScenario(session);
        session.write().body(graph(1, http(ORDERS))).put(scenario + "/graph").then().statusCode(200);
        return scenario;
    }

    static String createScenario(ApiSession session) {
        long project = session.write().body("{\"name\":\"Lab\"}").post("/api/v1/projects")
                .then().statusCode(201).extract().jsonPath().getLong("id");
        String path = "/api/v1/projects/" + project + "/scenarios";
        long id = session.write().body("{\"name\":\"Run\"}").post(path)
                .then().statusCode(201).extract().jsonPath().getLong("id");
        return path + "/" + id;
    }

    static String run(int revision) {
        return "{\"revision\":" + revision + "}";
    }

    private static String graph(int revision, String component) {
        return graph(revision, component, "");
    }

    private static String graph(int revision, String components, String connections) {
        return "{\"revision\":" + revision + ",\"components\":[" + components + "],\"connections\":[" + connections
                + "]}";
    }

    private static String http(String key) {
        return http(key, "STATELESS");
    }

    private static String http(String key, String profile) {
        return """
                {"key":"%s","templateKey":"http-service","name":"orders-api","position":{"x":0,"y":0},
                 "profile":"%s","parameters":{"responseDelayMs":0}}
                """.formatted(key, profile);
    }

    private static String postgresql(String key) {
        return """
                {"key":"%s","templateKey":"postgresql","name":"orders-db","position":{"x":0,"y":0},
                 "profile":"DEFAULT","parameters":{}}
                """.formatted(key);
    }

    private static String connection(String source, String port, String target) {
        return "{\"sourceKey\":\"%s\",\"port\":\"%s\",\"targetKey\":\"%s\"}".formatted(source, port, target);
    }

    private static String redis(String key) {
        return """
                {"key":"%s","templateKey":"redis","name":"cache","position":{"x":0,"y":0},
                 "profile":"DEFAULT","parameters":{}}
                """.formatted(key);
    }

    private static void readUntil(Iterator<String> lines, String text) {
        while (lines.hasNext()) {
            if (lines.next().contains(text)) {
                return;
            }
        }
        throw new AssertionError("The stream ended before sending " + text);
    }
}
