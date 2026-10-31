package es.codeurjc.infracture.project.api;

import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import es.codeurjc.infracture.project.persistence.ProjectRepository;
import es.codeurjc.infracture.project.persistence.ScenarioRepository;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class ScenarioGraphApiSystemTests {

    private static final String ORDERS = "3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10";
    private static final String USERS = "7a1d2c3b-4e5f-4a6b-8c9d-0e1f2a3b4c5d";
    private static final String CACHE = "0b9c8d7e-6f5a-4b3c-8d2e-1f0a9b8c7d6e";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioRepository scenarios;

    @Autowired
    private ProjectRepository projects;

    @BeforeEach
    void clearProjects() {
        scenarios.deleteAllInBatch();
        projects.deleteAllInBatch();
    }

    @Test
    void savesAndReopensTheGraphIncrementingTheRevision() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = createScenario(alice);

        alice.read().get(scenario + "/graph").then().statusCode(200)
            .body("revision", equalTo(1)).body("components", hasSize(0));

        alice.write().body(graph(1,
                http(ORDERS, "  orders-api  ", 120.5, -40, 0),
                http(USERS, "users-api", 400, 80, 250),
                redis(CACHE)))
            .put(scenario + "/graph").then().statusCode(200)
            .body("revision", equalTo(2))
            .body("components", hasSize(3))
            .body("issues", hasSize(0));

        alice.read().get(scenario + "/graph").then().statusCode(200)
            .body("revision", equalTo(2))
            .body("components.key", equalTo(List.of(ORDERS, USERS, CACHE)))
            .body("components[0].templateKey", equalTo("http-service"))
            .body("components[0].type", equalTo("HTTP_SERVICE"))
            .body("components[0].name", equalTo("orders-api"))
            .body("components[0].position.x", equalTo(120.5f))
            .body("components[0].position.y", equalTo(-40.0f))
            .body("components[0].profile", equalTo("STATELESS"))
            .body("components[0].parameters.responseDelayMs", equalTo(0))
            .body("components[1].parameters.responseDelayMs", equalTo(250))
            .body("components[2].profile", equalTo("DEFAULT"))
            .body("issues", hasSize(0));
        alice.read().get(scenario).then().statusCode(200).body("revision", equalTo(2));

        alice.write().body("{\"name\":\"Renamed\"}").put(scenario).then().statusCode(200).body("revision", equalTo(2));
        alice.write().body(graph(2)).put(scenario + "/graph").then().statusCode(200)
            .body("revision", equalTo(3)).body("components", hasSize(0));
    }

    @Test
    void rejectsASaveFromAStaleRevisionWithoutOverwriting() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = createScenario(alice);
        alice.write().body(graph(1, http(ORDERS, "first tab", 0, 0, 0))).put(scenario + "/graph")
            .then().statusCode(200);

        alice.write().body(graph(1, http(USERS, "second tab", 0, 0, 0))).put(scenario + "/graph")
            .then().spec(problem(409,
                "The scenario was saved after revision 1. Reload it to continue from revision 2."));

        alice.read().get(scenario + "/graph").then()
            .body("revision", equalTo(2)).body("components.name", equalTo(List.of("first tab")));
    }

    @Test
    void acceptsOnlyOneOfSeveralSimultaneousSavesFromTheSameRevision() throws Exception {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = createScenario(alice);
        int tabs = 6;
        RequestSpecification sameCsrfToken = alice.write();
        List<RequestSpecification> requests = IntStream.range(0, tabs)
            .mapToObj(tab -> given().spec(sameCsrfToken).body(graph(1, http(ORDERS, "tab " + tab, 0, 0, 0))))
            .toList();
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> saves = requests.stream().<Callable<Integer>>map(request -> () -> {
            start.await();
            return request.put(scenario + "/graph").statusCode();
        }).toList();

        ExecutorService executor = Executors.newFixedThreadPool(tabs);
        try {
            List<Future<Integer>> results = saves.stream().map(executor::submit).toList();
            start.countDown();
            List<Integer> statuses = results.stream().map(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();

            assertThat(statuses).containsOnly(200, 409).filteredOn(status -> status == 200).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
        alice.read().get(scenario + "/graph").then().body("revision", equalTo(2)).body("components", hasSize(1));
    }

    @Test
    void savesConfigurationOutsideTheContractAndReportsItAsAnIssue() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = createScenario(alice);

        alice.write().body(graph(1, http(ORDERS, "orders-api", 0, 0, 9000))).put(scenario + "/graph")
            .then().statusCode(200)
            .body("revision", equalTo(2))
            .body("issues", hasSize(1))
            .body("issues[0].severity", equalTo("ERROR"))
            .body("issues[0].code", equalTo("PARAMETER_OUT_OF_RANGE"))
            .body("issues[0].message", equalTo("responseDelayMs must be between 0 and 5000"))
            .body("issues[0].target.kind", equalTo("COMPONENT"))
            .body("issues[0].target.key", equalTo(ORDERS))
            .body("issues[0].field", equalTo("responseDelayMs"));

        alice.read().get(scenario + "/graph").then()
            .body("components[0].parameters.responseDelayMs", equalTo(9000))
            .body("issues.code", equalTo(List.of("PARAMETER_OUT_OF_RANGE")));
    }

    @Test
    void rejectsStructuralProblemsWithoutSaving() {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = createScenario(alice);

        alice.write().body(graph(1, node(ORDERS, "docker-image", "evil", "DEFAULT", "{}"))).put(scenario + "/graph")
            .then().spec(problem(400, "Component evil: unknown or disabled template docker-image"));
        alice.write().body(graph(1, node(ORDERS, "load-generator", "load", "CONSTANT_HTTP", "{}")))
            .put(scenario + "/graph")
            .then().spec(problem(400, "Component load: unknown or disabled template load-generator"));
        alice.write().body(graph(1, redis(ORDERS), redis(ORDERS))).put(scenario + "/graph")
            .then().spec(problem(400, "Component " + ORDERS + " appears more than once"));
        alice.write().body(graph(1, node(ORDERS, "http-service", "orders-api", "STATELESS",
                "{\"responseDelayMs\":250.5}"))).put(scenario + "/graph").then().statusCode(400);

        alice.read().get(scenario + "/graph").then().body("revision", equalTo(1)).body("components", hasSize(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image", "command", "ports", "type", "contractVersion", "id"})
    void rejectsFieldsOutsideTheGraphContract(String field) {
        ApiSession alice = ApiSession.signUp(port);
        String scenario = createScenario(alice);
        String tampered = """
                {"key":"%s","templateKey":"redis","name":"cache","position":{"x":0,"y":0},
                 "profile":null,"parameters":{},"%s":"nginx:latest"}
                """.formatted(ORDERS, field);

        alice.write().body(graph(1, tampered)).put(scenario + "/graph").then().statusCode(400);
        alice.write().body("{\"revision\":1,\"components\":[],\"" + field + "\":1}").put(scenario + "/graph")
            .then().statusCode(400);
        alice.write().body("{\"components\":[]}").put(scenario + "/graph").then().statusCode(400);

        alice.read().get(scenario + "/graph").then().body("revision", equalTo(1));
    }

    @Test
    void hidesTheGraphOfOtherAccountsAndProjects() {
        ApiSession alice = ApiSession.signUp(port);
        ApiSession bob = ApiSession.signUp(port);
        String scenario = createScenario(alice);
        String bobProject = "/api/v1/projects/" + createProject(bob);
        String scenarioId = scenario.substring(scenario.lastIndexOf('/') + 1);

        bob.read().get(scenario + "/graph").then().spec(problem(404, "Project not found"));
        bob.write().body(graph(1)).put(scenario + "/graph").then().spec(problem(404, "Project not found"));
        bob.write().body(graph(1)).put(bobProject + "/scenarios/" + scenarioId + "/graph")
            .then().spec(problem(404, "Scenario not found"));
        given().port(port).get(scenario + "/graph").then().statusCode(401);

        alice.read().get(scenario + "/graph").then().body("revision", equalTo(1));
    }

    private static String graph(int revision, String... components) {
        return "{\"revision\":" + revision + ",\"components\":[" + String.join(",", components) + "]}";
    }

    private static String http(String key, String name, double x, double y, int delay) {
        return """
                {"key":"%s","templateKey":"http-service","name":"%s","position":{"x":%s,"y":%s},
                 "profile":"STATELESS","parameters":{"responseDelayMs":%d}}
                """.formatted(key, name, x, y, delay);
    }

    private static String redis(String key) {
        return node(key, "redis", "cache", "DEFAULT", "{}");
    }

    private static String node(String key, String template, String name, String profile, String parameters) {
        return """
                {"key":"%s","templateKey":"%s","name":"%s","position":{"x":0,"y":0},
                 "profile":"%s","parameters":%s}
                """.formatted(key, template, name, profile, parameters);
    }

    private String createScenario(ApiSession session) {
        String scenarios = "/api/v1/projects/" + createProject(session) + "/scenarios";
        long id = session.write().body("{\"name\":\"Cold cache\"}").post(scenarios)
            .then().statusCode(201).extract().jsonPath().getLong("id");
        return scenarios + "/" + id;
    }

    private long createProject(ApiSession session) {
        return session.write().body("{\"name\":\"Cache lab\"}").post("/api/v1/projects")
            .then().statusCode(201).extract().jsonPath().getLong("id");
    }
}
