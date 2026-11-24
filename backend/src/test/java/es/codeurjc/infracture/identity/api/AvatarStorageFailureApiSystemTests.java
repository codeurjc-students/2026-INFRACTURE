package es.codeurjc.infracture.identity.api;

import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import es.codeurjc.infracture.identity.TestImages;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Stops the storage server in the middle, so it has its own containers.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource("classpath:admin-test.properties")
@Testcontainers
class AvatarStorageFailureApiSystemTests {

    private static final String EMAIL = "failure@example.com";
    private static final String PASSWORD = "A secure integration password 2026!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @Container
    static final GenericContainer<?> rustfs = new GenericContainer<>("rustfs/rustfs:1.0.6")
            .withEnv("RUSTFS_ACCESS_KEY", "failure-test")
            .withEnv("RUSTFS_SECRET_KEY", "failure-test-secret")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/health").forPort(9000));

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("infracture.storage.endpoint",
                () -> "http://%s:%d".formatted(rustfs.getHost(), rustfs.getMappedPort(9000)));
        registry.add("infracture.storage.access-key", () -> "failure-test");
        registry.add("infracture.storage.secret-key", () -> "failure-test-secret");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository repository;

    @Test
    void aReplacementDuringAStorageOutageKeepsThePreviousAvatar() {
        CookieFilter cookies = new CookieFilter();
        Long id = csrf(cookies).contentType(ContentType.JSON).body("""
                {"username":"failure","displayName":"Failure User","email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD)).post("/api/v1/users")
                .then().statusCode(201).extract().jsonPath().getLong("id");
        csrf(cookies).contentType(ContentType.JSON).body("""
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD)).post("/api/v1/auth/login").then().statusCode(200);
        String avatarUrl = csrf(cookies).multiPart("file", "avatar", TestImages.png(200, 200), "image/png")
                .put("/api/v1/users/me/avatar").then().statusCode(200).extract().path("avatarUrl");
        String key = repository.findById(id).orElseThrow().getAvatarKey();

        rustfs.stop();

        csrf(cookies).multiPart("file", "avatar", TestImages.png(256, 256), "image/png")
                .put("/api/v1/users/me/avatar")
                .then().spec(problem(503, "Image storage is unavailable. Try again later."));
        given().port(port).filter(cookies).get("/api/v1/users/me")
                .then().statusCode(200).body("avatarUrl", equalTo(avatarUrl));
        assertThat(repository.findById(id).orElseThrow().getAvatarKey()).isEqualTo(key);
    }

    private RequestSpecification csrf(CookieFilter cookies) {
        given().port(port).filter(cookies).get("/api/v1/auth/csrf").then().statusCode(204);
        String token = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .findFirst().orElseThrow().getValue();
        return given().port(port).filter(cookies).header("X-XSRF-TOKEN", token);
    }
}
