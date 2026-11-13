package es.codeurjc.infracture.catalog.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import es.codeurjc.infracture.project.api.ApiSession;
import java.util.List;
import org.junit.jupiter.api.Test;
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
class WorkloadProfileApiSystemTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @LocalServerPort
    private int port;

    @Test
    void listsTheLoadProfilesWithTheLimitsTheValidatorApplies() {
        ApiSession.signUp(port).read().get("/api/v1/workload-profiles").then()
            .statusCode(200)
            .body("name", contains("CONSTANT_HTTP"))
            .body("[0].parameters.name", contains("requestsPerSecond", "virtualUsers", "durationSeconds"))
            .body("[0].parameters.min", contains(1, 1, 10))
            .body("[0].parameters.max", contains(50, 20, 600))
            .body("[0].seed.name", equalTo("seed"))
            .body("[0].seed.min", equalTo(0))
            .body("[0].seed.max", equalTo(Integer.MAX_VALUE))
            .body("[0].requires", equalTo(List.of("HTTP")))
            .body("[0].initialParameters.requestsPerSecond", equalTo(5))
            .body("[0].initialParameters.virtualUsers", equalTo(2))
            .body("[0].initialParameters.durationSeconds", equalTo(60));
    }

    @Test
    void asksForASession() {
        given().port(port).get("/api/v1/workload-profiles").then().statusCode(401);
    }
}
