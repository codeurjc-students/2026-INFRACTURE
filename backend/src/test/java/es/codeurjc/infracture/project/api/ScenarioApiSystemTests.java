package es.codeurjc.infracture.project.api;

import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import es.codeurjc.infracture.project.persistence.ProjectRepository;
import es.codeurjc.infracture.project.persistence.ScenarioRepository;
import io.restassured.response.Response;
import java.util.List;
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
class ScenarioApiSystemTests {

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
    void createsOpensAndRenamesEmptyScenarioInOwnProject() {
        ApiSession alice = signUp();
        long projectId = createProject(alice, "Cache lab");
        String scenariosPath = "/api/v1/projects/" + projectId + "/scenarios";

        Response created = alice.write().body("""
                {"name":"  Cold cache  ","description":"Empty Redis"}
                """).post(scenariosPath);
        created.then().statusCode(201)
            .body("name", equalTo("Cold cache"))
            .body("description", equalTo("Empty Redis"))
            .body("revision", equalTo(1));
        long id = created.jsonPath().getLong("id");
        assertThat(created.header("Location")).endsWith(scenariosPath + "/" + id);
        long otherId = alice.write().body("{\"name\":\"Warm cache\"}").post(scenariosPath)
            .then().statusCode(201).extract().jsonPath().getLong("id");

        alice.write().body("{\"name\":\"Renamed\",\"description\":\"\"}").put(scenariosPath + "/" + id)
            .then().statusCode(200).body("name", equalTo("Renamed")).body("description", nullValue())
            .body("revision", equalTo(1));
        alice.read().get(created.header("Location")).then().statusCode(200)
            .body("name", equalTo("Renamed")).body("revision", equalTo(1));
        alice.read().get(scenariosPath + "/" + otherId).then().statusCode(200).body("name", equalTo("Warm cache"));
        alice.read().get("/api/v1/projects/" + projectId).then().statusCode(200).body("name", equalTo("Cache lab"));
    }

    @Test
    void pagesScenariosOfTheProjectNewestFirst() {
        ApiSession alice = signUp();
        long cacheLab = createProject(alice, "Cache lab");
        long queueLab = createProject(alice, "Queue lab");
        for (int number = 1; number <= 11; number++) {
            alice.write().body("{\"name\":\"Cache " + number + "\"}")
                .post("/api/v1/projects/" + cacheLab + "/scenarios").then().statusCode(201);
        }
        alice.write().body("{\"name\":\"Queue\"}").post("/api/v1/projects/" + queueLab + "/scenarios")
            .then().statusCode(201);

        alice.read().get("/api/v1/projects/" + cacheLab + "/scenarios").then().statusCode(200)
            .body("content", hasSize(10))
            .body("content[0].name", equalTo("Cache 11"))
            .body("page.totalElements", equalTo(11))
            .body("page.totalPages", equalTo(2));
        alice.read().queryParam("page", 1).get("/api/v1/projects/" + cacheLab + "/scenarios").then()
            .statusCode(200).body("content.name", equalTo(List.of("Cache 1")));
        alice.read().queryParam("size", 101).get("/api/v1/projects/" + cacheLab + "/scenarios")
            .then().statusCode(400);
    }

    @Test
    void hidesScenariosOfOtherAccountsAndProjects() {
        ApiSession alice = signUp();
        ApiSession bob = signUp();
        long aliceLab = createProject(alice, "Alice lab");
        long otherAliceLab = createProject(alice, "Other lab");
        long bobLab = createProject(bob, "Bob lab");
        long id = alice.write().body("{\"name\":\"Private\"}").post("/api/v1/projects/" + aliceLab + "/scenarios")
            .then().statusCode(201).extract().jsonPath().getLong("id");

        bob.read().get("/api/v1/projects/" + aliceLab + "/scenarios")
            .then().spec(problem(404, "Project not found"));
        bob.read().get("/api/v1/projects/" + aliceLab + "/scenarios/" + id)
            .then().spec(problem(404, "Project not found"));
        bob.read().get("/api/v1/projects/" + bobLab + "/scenarios/" + id)
            .then().spec(problem(404, "Scenario not found"));
        bob.write().body("{\"name\":\"Taken over\"}").put("/api/v1/projects/" + bobLab + "/scenarios/" + id)
            .then().spec(problem(404, "Scenario not found"));
        bob.write().body("{\"name\":\"Planted\"}").post("/api/v1/projects/" + aliceLab + "/scenarios")
            .then().spec(problem(404, "Project not found"));
        alice.read().get("/api/v1/projects/" + otherAliceLab + "/scenarios/" + id)
            .then().spec(problem(404, "Scenario not found"));
        alice.read().get("/api/v1/projects/" + aliceLab + "/scenarios/" + (id + 1000))
            .then().spec(problem(404, "Scenario not found"));
        given().port(port).get("/api/v1/projects/" + aliceLab + "/scenarios").then().statusCode(401);

        assertThat(scenarios.findAll()).singleElement().satisfies(scenario -> {
            assertThat(scenario.getName()).isEqualTo("Private");
            assertThat(scenario.getProject().getId()).isEqualTo(aliceLab);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "projectId", "project", "revision", "createdAt"})
    void rejectsFieldsControlledByTheServer(String field) {
        ApiSession alice = signUp();
        long projectId = createProject(alice, "Cache lab");
        long otherProjectId = createProject(alice, "Queue lab");
        String scenariosPath = "/api/v1/projects/" + projectId + "/scenarios";
        long id = alice.write().body("{\"name\":\"Cold cache\"}").post(scenariosPath)
            .then().statusCode(201).extract().jsonPath().getLong("id");
        String tampered = "{\"name\":\"Tampered\",\"" + field + "\":" + otherProjectId + "}";

        alice.write().body(tampered).post(scenariosPath).then().statusCode(400);
        alice.write().body(tampered).put(scenariosPath + "/" + id).then().statusCode(400);

        assertThat(scenarios.findAll()).singleElement().satisfies(scenario -> {
            assertThat(scenario.getName()).isEqualTo("Cold cache");
            assertThat(scenario.getProject().getId()).isEqualTo(projectId);
            assertThat(scenario.getRevision()).isEqualTo(1);
        });
    }

    @Test
    void rejectsInvalidScenarioDetails() {
        ApiSession alice = signUp();
        String scenariosPath = "/api/v1/projects/" + createProject(alice, "Cache lab") + "/scenarios";

        alice.write().body("{\"name\":\"   \"}").post(scenariosPath)
            .then().spec(problem(400, "Scenario name is required"));
        long id = alice.write().body("{\"name\":\"Cold cache\"}").post(scenariosPath)
            .then().statusCode(201).extract().jsonPath().getLong("id");
        alice.write().body("{\"name\":\"Cold cache\",\"description\":\"" + "d".repeat(281) + "\"}")
            .put(scenariosPath + "/" + id)
            .then().spec(problem(400, "Scenario description must contain at most 280 characters"));
        alice.read().get(scenariosPath + "/" + id).then().body("description", nullValue());
    }

    private ApiSession signUp() {
        return ApiSession.signUp(port);
    }

    private long createProject(ApiSession session, String name) {
        return session.write().body("{\"name\":\"" + name + "\"}").post("/api/v1/projects")
            .then().statusCode(201).extract().jsonPath().getLong("id");
    }
}
