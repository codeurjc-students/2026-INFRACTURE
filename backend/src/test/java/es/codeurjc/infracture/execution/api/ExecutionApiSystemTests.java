package es.codeurjc.infracture.execution.api;

import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.project.api.ApiSession;
import es.codeurjc.infracture.project.persistence.ProjectRepository;
import es.codeurjc.infracture.project.persistence.ScenarioRepository;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource("classpath:admin-test.properties")
@Testcontainers
class ExecutionApiSystemTests {

    private static final String ORDERS = "3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10";
    private static final String CACHE = "0b9c8d7e-6f5a-4b3c-8d2e-1f0a9b8c7d6e";
    private static final String ANOTHER_RUNNING = "Another experiment is running. Run this scenario when it ends.";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private ExecutionRepository executions;

    @Autowired
    private ScenarioRepository scenarios;

    @Autowired
    private ProjectRepository projects;

    @BeforeEach
    void clearData() {
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
            .extract().jsonPath().getLong("id");

        alice.read().get(scenario + "/executions/current").then().statusCode(200)
            .body("id", equalTo((int) id)).body("phase", equalTo("RESERVED"));
        String readOnly = "The scenario is read-only while an experiment uses it. Stop it to edit the scenario.";
        alice.write().body(graph(2, "")).put(scenario + "/graph").then().spec(problem(409, readOnly));
        alice.write().body("{\"name\":\"Renamed\"}").put(scenario).then().spec(problem(409, readOnly));
        alice.read().get(scenario + "/graph").then().body("revision", equalTo(2));
        alice.write().body(graph(1, http(ORDERS))).put(other + "/graph").then().statusCode(200);
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

    private String statelessScenario(ApiSession session) {
        String scenario = createScenario(session);
        session.write().body(graph(1, http(ORDERS))).put(scenario + "/graph").then().statusCode(200);
        return scenario;
    }

    private String createScenario(ApiSession session) {
        long project = session.write().body("{\"name\":\"Lab\"}").post("/api/v1/projects")
            .then().statusCode(201).extract().jsonPath().getLong("id");
        String scenarios = "/api/v1/projects/" + project + "/scenarios";
        long id = session.write().body("{\"name\":\"Run\"}").post(scenarios)
            .then().statusCode(201).extract().jsonPath().getLong("id");
        return scenarios + "/" + id;
    }

    private static String run(int revision) {
        return "{\"revision\":" + revision + "}";
    }

    private static String graph(int revision, String component) {
        return "{\"revision\":" + revision + ",\"components\":[" + component + "],\"connections\":[]}";
    }

    private static String http(String key) {
        return """
                {"key":"%s","templateKey":"http-service","name":"orders-api","position":{"x":0,"y":0},
                 "profile":"STATELESS","parameters":{"responseDelayMs":0}}
                """.formatted(key);
    }

    private static String redis(String key) {
        return """
                {"key":"%s","templateKey":"redis","name":"cache","position":{"x":0,"y":0},
                 "profile":"DEFAULT","parameters":{}}
                """.formatted(key);
    }
}
