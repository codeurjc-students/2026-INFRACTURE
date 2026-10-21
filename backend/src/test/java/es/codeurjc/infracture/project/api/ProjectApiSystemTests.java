package es.codeurjc.infracture.project.api;

import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import es.codeurjc.infracture.project.persistence.ProjectRepository;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
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
class ProjectApiSystemTests {

    private static final String PASSWORD = "A secure integration password 2026!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRepository repository;

    private static int accounts;

    @BeforeEach
    void clearProjects() {
        repository.deleteAllInBatch();
    }

    @Test
    void createsReadsAndEditsOwnPrivateProject() {
        Session alice = signUp();

        Response created = alice.write().body("""
                {"name":"  Cache lab  ","description":"Redis in front of PostgreSQL"}
                """).post("/api/v1/projects");
        created.then().statusCode(201)
            .body("name", equalTo("Cache lab"))
            .body("description", equalTo("Redis in front of PostgreSQL"));
        String location = created.header("Location");
        long id = created.jsonPath().getLong("id");
        assertThat(location).endsWith("/api/v1/projects/" + id);

        alice.read().get(location).then().statusCode(200).body("name", equalTo("Cache lab"));

        alice.write().body("""
                {"name":"Renamed lab","description":""}
                """).put("/api/v1/projects/" + id)
            .then().statusCode(200).body("name", equalTo("Renamed lab")).body("description", nullValue());
        alice.read().get("/api/v1/projects/" + id).then().statusCode(200)
            .body("name", equalTo("Renamed lab")).body("description", nullValue());
    }

    @Test
    void pagesOnlyOwnProjectsNewestFirst() {
        Session alice = signUp();
        Session bob = signUp();
        for (int number = 1; number <= 11; number++) {
            alice.write().body("{\"name\":\"Alice lab " + number + "\"}").post("/api/v1/projects")
                .then().statusCode(201);
        }
        bob.write().body("{\"name\":\"Bob lab\"}").post("/api/v1/projects").then().statusCode(201);

        alice.read().get("/api/v1/projects").then().statusCode(200)
            .body("content", hasSize(10))
            .body("content[0].name", equalTo("Alice lab 11"))
            .body("page.totalElements", equalTo(11))
            .body("page.totalPages", equalTo(2));
        alice.read().queryParam("page", 1).get("/api/v1/projects").then().statusCode(200)
            .body("content.name", equalTo(java.util.List.of("Alice lab 1")));
        bob.read().get("/api/v1/projects").then().statusCode(200)
            .body("content.name", equalTo(java.util.List.of("Bob lab")));
        alice.read().queryParam("size", 101).get("/api/v1/projects").then().statusCode(400);
    }

    @Test
    void hidesProjectsFromOtherAccountsAndVisitors() {
        Session alice = signUp();
        Session bob = signUp();
        long id = alice.write().body("{\"name\":\"Private lab\"}").post("/api/v1/projects")
            .then().statusCode(201).extract().jsonPath().getLong("id");

        bob.read().get("/api/v1/projects/" + id).then().statusCode(404);
        bob.write().body("{\"name\":\"Taken over\"}").put("/api/v1/projects/" + id).then().statusCode(404);
        given().port(port).get("/api/v1/projects/" + id).then().statusCode(401);
        given().port(port).get("/api/v1/projects").then().statusCode(401);

        assertThat(repository.findById(id)).get().extracting(project -> project.getName()).isEqualTo("Private lab");
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "ownerId", "owner", "createdAt", "visibility"})
    void rejectsFieldsControlledByTheServerWithoutCreatingProject(String field) {
        Session alice = signUp();

        alice.write().body("{\"name\":\"Tampered\",\"" + field + "\":1}").post("/api/v1/projects")
            .then().statusCode(400);
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsInvalidProjectDetails() {
        Session alice = signUp();

        alice.write().body("{\"name\":\"   \"}").post("/api/v1/projects")
            .then().statusCode(400).body("detail", org.hamcrest.Matchers.containsString("Project name is required"));
        alice.write().body("{\"name\":\"" + "n".repeat(31) + "\"}").post("/api/v1/projects")
            .then().statusCode(400);
        long id = alice.write().body("{\"name\":\"Valid lab\"}").post("/api/v1/projects")
            .then().statusCode(201).extract().jsonPath().getLong("id");
        alice.write().body("{\"name\":\"Valid lab\",\"description\":\"" + "d".repeat(281) + "\"}")
            .put("/api/v1/projects/" + id).then().statusCode(400);
        alice.read().get("/api/v1/projects/" + id).then().body("description", nullValue());
    }

    @Test
    void returnsServiceValidationAsProblemDetail() {
        Session alice = signUp();

        alice.write().body("{\"name\":\"   \"}").post("/api/v1/projects")
            .then().spec(problem(400, "Project name is required"));
    }

    @Test
    void returnsMalformedRequestsAsProblemDetail() {
        Session alice = signUp();

        alice.read().queryParam("size", 101).get("/api/v1/projects")
            .then().spec(problem(400, not(emptyOrNullString())));
        alice.read().get("/api/v1/projects/not-a-number")
            .then().spec(problem(400, not(emptyOrNullString())));
        alice.write().body("{\"name\":").post("/api/v1/projects")
            .then().spec(problem(400, not(emptyOrNullString())));
        alice.write().body("{\"name\":\"Tampered\",\"ownerId\":1}").post("/api/v1/projects")
            .then().spec(problem(400, not(emptyOrNullString())));
    }

    @Test
    void returnsMissingOrOtherUsersProjectAsProblemDetail() {
        Session alice = signUp();
        Session bob = signUp();
        long id = alice.write().body("{\"name\":\"Private lab\"}").post("/api/v1/projects")
            .then().statusCode(201).extract().jsonPath().getLong("id");

        bob.read().get("/api/v1/projects/" + id).then().spec(problem(404, "Project not found"));
        alice.read().get("/api/v1/projects/" + (id + 1000)).then().spec(problem(404, "Project not found"));
    }

    @Test
    void returnsUnknownApiRouteAsProblemDetail() {
        Session alice = signUp();

        alice.read().get("/api/v1/unknown-resource")
            .then().spec(problem(404, "The requested resource does not exist."));
    }

    @Test
    void returnsMethodNotAllowedAsProblemDetail() {
        Session alice = signUp();
        long id = alice.write().body("{\"name\":\"Kept lab\"}").post("/api/v1/projects")
            .then().statusCode(201).extract().jsonPath().getLong("id");

        alice.write().delete("/api/v1/projects/" + id).then().spec(problem(405, not(emptyOrNullString())));
        assertThat(repository.existsById(id)).isTrue();
    }

    private Session signUp() {
        accounts++;
        String username = "projects_user_" + accounts;
        String email = username + "@example.com";
        CookieFilter cookies = new CookieFilter();
        Session session = new Session(cookies);
        session.write().body("""
                {"username":"%s","displayName":"Project User","email":"%s","password":"%s"}
                """.formatted(username, email, PASSWORD)).post("/api/v1/users").then().statusCode(201);
        session.write().body("""
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD)).post("/api/v1/auth/login").then().statusCode(200);
        return session;
    }

    private final class Session {

        private final CookieFilter cookies;

        private Session(CookieFilter cookies) {
            this.cookies = cookies;
        }

        RequestSpecification read() {
            return given().port(port).filter(cookies);
        }

        RequestSpecification write() {
            given().port(port).filter(cookies).get("/api/v1/auth/csrf").then().statusCode(204);
            String csrf = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .findFirst().orElseThrow().getValue();
            return given().port(port).filter(cookies).header("X-XSRF-TOKEN", csrf).contentType(ContentType.JSON);
        }
    }
}
