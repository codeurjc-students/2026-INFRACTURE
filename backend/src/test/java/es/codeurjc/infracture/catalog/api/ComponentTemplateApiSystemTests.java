package es.codeurjc.infracture.catalog.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
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
                .body("size()", equalTo(6))
                .body("key", contains(
                        "http-service",
                        "worker",
                        "load-generator",
                        "postgresql",
                        "redis",
                        "rabbitmq"))
                .body("[0].name", equalTo("HTTP Service"))
                .body("[0].type", equalTo("HTTP_SERVICE"))
                .body("[5].name", equalTo("RabbitMQ"))
                .body("[5].type", equalTo("RABBITMQ"));
    }
}
