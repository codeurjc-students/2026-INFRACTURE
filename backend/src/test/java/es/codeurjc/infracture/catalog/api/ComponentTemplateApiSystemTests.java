package es.codeurjc.infracture.catalog.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource("classpath:admin-test.properties")
@Testcontainers
class ComponentTemplateApiSystemTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @LocalServerPort
    private int port;

    @Test
    void returnsSeededCatalogueThroughHttp() {
        given()
                .port(port)
        .when()
                .get("/api/v1/component-templates")
        .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("content.size()", equalTo(5))
                .body("page.number", equalTo(0))
                .body("page.size", equalTo(10))
                .body("page.totalElements", equalTo(5))
                .body("page.totalPages", equalTo(1))
                .body("content.key", contains(
                        "http-service",
                        "postgresql",
                        "rabbitmq",
                        "redis",
                        "worker"))
                .body("content[0].name", equalTo("HTTP Service"))
                .body("content[0].type", equalTo("HTTP_SERVICE"))
                .body("content[4].name", equalTo("Worker"))
                .body("content[4].type", equalTo("WORKER"));
    }

    @Test
    void describesTheContractAndInitialConfigurationOfEachTemplate() {
        given()
                .port(port)
                .queryParam("type", "HTTP_SERVICE")
        .when()
                .get("/api/v1/component-templates")
        .then()
                .statusCode(200)
                .body("content[0].contract.version", equalTo(1))
                .body("content[0].contract.profiles[0].name", equalTo("STATELESS"))
                .body("content[0].contract.profiles[0].parameters[0].name", equalTo("responseDelayMs"))
                .body("content[0].contract.profiles[0].parameters[0].min", equalTo(0))
                .body("content[0].contract.profiles[0].parameters[0].max", equalTo(5000))
                .body("content[0].initialConfiguration.profile", equalTo("STATELESS"))
                .body("content[0].initialConfiguration.parameters.responseDelayMs", equalTo(0));

        given()
                .port(port)
                .queryParam("type", "LOAD_GENERATOR")
        .when()
                .get("/api/v1/component-templates")
        .then()
                .statusCode(400);
    }

    @Test
    void returnsEmptyPageWhenNameAndTypeDoNotMatchTogether() {
        given()
                .port(port)
                .queryParam("q", "redis")
                .queryParam("type", "WORKER")
                .queryParam("size", 1)
        .when()
                .get("/api/v1/component-templates")
        .then()
                .statusCode(200)
                .body("content.size()", equalTo(0))
                .body("page.number", equalTo(0))
                .body("page.size", equalTo(1))
                .body("page.totalElements", equalTo(0))
                .body("page.totalPages", equalTo(0));
    }

    @ParameterizedTest
    @CsvSource({
            "page, -1",
            "page, abc",
            "size, 0",
            "size, 101",
            "type, UNSUPPORTED"
    })
    void rejectsInvalidQueryParameters(String parameter, String value) {
        given()
                .port(port)
                .queryParam(parameter, value)
        .when()
                .get("/api/v1/component-templates")
        .then()
                .statusCode(400);
    }

    @Test
    void returnsEmptyHighPageWithFilteredTotals() {
        given()
                .port(port)
                .queryParam("page", 10001)
                .queryParam("q", "redis")
                .queryParam("type", "REDIS")
        .when()
                .get("/api/v1/component-templates")
        .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("content.size()", equalTo(0))
                .body("page.number", equalTo(10001))
                .body("page.size", equalTo(10))
                .body("page.totalElements", equalTo(1))
                .body("page.totalPages", equalTo(1));
    }
}
