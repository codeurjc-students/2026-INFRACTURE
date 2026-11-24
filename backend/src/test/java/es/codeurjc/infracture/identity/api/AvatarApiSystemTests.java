package es.codeurjc.infracture.identity.api;

import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

import es.codeurjc.infracture.identity.TestImages;
import es.codeurjc.infracture.identity.application.AvatarService;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import es.codeurjc.infracture.shared.storage.ObjectStorage;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource("classpath:admin-test.properties")
@Testcontainers
class AvatarApiSystemTests {

    private static final String PASSWORD = "A secure integration password 2026!";
    private static final AtomicInteger ACCOUNTS = new AtomicInteger();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @Container
    static final GenericContainer<?> rustfs = new GenericContainer<>("rustfs/rustfs:1.0.6")
            .withEnv("RUSTFS_ACCESS_KEY", "avatar-test")
            .withEnv("RUSTFS_SECRET_KEY", "avatar-test-secret")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/health").forPort(9000));

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("infracture.storage.endpoint",
                () -> "http://%s:%d".formatted(rustfs.getHost(), rustfs.getMappedPort(9000)));
        registry.add("infracture.storage.access-key", () -> "avatar-test");
        registry.add("infracture.storage.secret-key", () -> "avatar-test-secret");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository repository;

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private AvatarService avatars;

    @Test
    void uploadsReadsAndReplacesTheAvatarOfTheSignedInAccount() {
        Account alice = register();
        me(alice.cookies).then().statusCode(200).body("avatarUrl", nullValue());
        read(alice.cookies, "/api/v1/users/me/avatar").then().spec(problem(404, "The account has no avatar"));

        String firstUrl = upload(alice.cookies, TestImages.jpeg(300, 300), "image/jpeg").then().statusCode(200)
                .body("avatarUrl", matchesPattern("/api/v1/users/me/avatar\\?v=[0-9a-f-]{36}"))
                .extract().path("avatarUrl");

        Response image = read(alice.cookies, firstUrl);
        image.then().statusCode(200).contentType("image/png").header("Cache-Control", containsString("immutable"));
        BufferedImage stored = TestImages.decode(image.asByteArray());
        assertThat(stored.getWidth()).isEqualTo(300);
        assertThat(stored.getHeight()).isEqualTo(300);
        read(alice.cookies, "/api/v1/users/me/avatar").then().statusCode(200)
                .header("Cache-Control", containsString("no-cache"));

        String secondUrl = upload(alice.cookies, TestImages.png(200, 200), "image/png").then().statusCode(200)
                .extract().path("avatarUrl");

        assertThat(secondUrl).isNotEqualTo(firstUrl);
        assertThat(TestImages.decode(read(alice.cookies, secondUrl).asByteArray()).getWidth()).isEqualTo(200);
        assertThat(storage.keys(prefix(alice)))
                .containsExactly(repository.findById(alice.id).orElseThrow().getAvatarKey());
    }

    @Test
    void rejectsFilesThatAreNotValidAvatarsAndKeepsTheCurrentOne() {
        Account alice = register();
        upload(alice.cookies, TestImages.png(200, 200), "image/png").then().statusCode(200);
        String key = repository.findById(alice.id).orElseThrow().getAvatarKey();

        upload(alice.cookies, "<svg onload='alert(1)'/>".getBytes(), "image/svg+xml")
                .then().spec(problem(400, "The avatar must be a PNG or JPEG image"));
        upload(alice.cookies, TestImages.png(300, 200), "image/png")
                .then().spec(problem(400, "The avatar must be square"));
        upload(alice.cookies, new byte[3 * 1024 * 1024], "image/png").then().statusCode(413);
        csrf(alice.cookies).contentType(ContentType.MULTIPART).multiPart("other", "x").put("/api/v1/users/me/avatar")
                .then().statusCode(400);

        assertThat(repository.findById(alice.id).orElseThrow().getAvatarKey()).isEqualTo(key);
        assertThat(storage.keys(prefix(alice))).containsExactly(key);
    }

    @Test
    void removesTheAvatarAndItsImage() {
        Account alice = register();
        upload(alice.cookies, TestImages.png(200, 200), "image/png").then().statusCode(200);

        csrf(alice.cookies).delete("/api/v1/users/me/avatar").then().statusCode(200).body("avatarUrl", nullValue());

        read(alice.cookies, "/api/v1/users/me/avatar").then().statusCode(404);
        assertThat(storage.keys(prefix(alice))).isEmpty();
        csrf(alice.cookies).delete("/api/v1/users/me/avatar").then().statusCode(200).body("avatarUrl", nullValue());
    }

    @Test
    void keepsEachAvatarToItsOwner() {
        Account alice = register();
        Account bob = register();
        upload(alice.cookies, TestImages.png(200, 200), "image/png").then().statusCode(200);
        String aliceKey = repository.findById(alice.id).orElseThrow().getAvatarKey();
        CookieFilter anonymous = new CookieFilter();

        read(anonymous, "/api/v1/users/me/avatar").then().spec(problem(401, "Authentication is required."));
        upload(anonymous, TestImages.png(200, 200), "image/png").then().statusCode(401);
        read(bob.cookies, "/api/v1/users/me/avatar").then().statusCode(404);
        upload(bob.cookies, TestImages.png(256, 256), "image/png").then().statusCode(200);

        assertThat(repository.findById(alice.id).orElseThrow().getAvatarKey()).isEqualTo(aliceKey);
        assertThat(TestImages.decode(read(alice.cookies, "/api/v1/users/me/avatar").asByteArray()).getWidth())
                .isEqualTo(200);
    }

    @Test
    void theStartupCleanupRemovesOnlyUnusedImages() {
        Account alice = register();
        upload(alice.cookies, TestImages.png(200, 200), "image/png").then().statusCode(200);
        String aliceKey = repository.findById(alice.id).orElseThrow().getAvatarKey();
        storage.put("avatars/999999/left-by-a-failed-upload.png", TestImages.png(200, 200), "image/png");

        avatars.afterSingletonsInstantiated();

        assertThat(storage.keys("avatars/999999/")).isEmpty();
        assertThat(storage.keys(prefix(alice))).containsExactly(aliceKey);
    }

    private record Account(Long id, CookieFilter cookies) {
    }

    private Account register() {
        String username = "avatar" + ACCOUNTS.incrementAndGet();
        String email = username + "@example.com";
        CookieFilter cookies = new CookieFilter();
        Long id = csrf(cookies).contentType(ContentType.JSON).body("""
                {"username":"%s","displayName":"Avatar User","email":"%s","password":"%s"}
                """.formatted(username, email, PASSWORD)).post("/api/v1/users")
                .then().statusCode(201).extract().jsonPath().getLong("id");
        csrf(cookies).contentType(ContentType.JSON).body("""
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD)).post("/api/v1/auth/login").then().statusCode(200);
        return new Account(id, cookies);
    }

    private Response upload(CookieFilter cookies, byte[] content, String mimeType) {
        return csrf(cookies).multiPart("file", "avatar", content, mimeType).put("/api/v1/users/me/avatar");
    }

    private Response read(CookieFilter cookies, String url) {
        return given().port(port).filter(cookies).urlEncodingEnabled(false).get(url);
    }

    private Response me(CookieFilter cookies) {
        return given().port(port).filter(cookies).get("/api/v1/users/me");
    }

    private static String prefix(Account account) {
        return "avatars/" + account.id + "/";
    }

    private RequestSpecification csrf(CookieFilter cookies) {
        given().port(port).filter(cookies).get("/api/v1/auth/csrf").then().statusCode(204);
        String token = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .findFirst().orElseThrow().getValue();
        return given().port(port).filter(cookies).header("X-XSRF-TOKEN", token);
    }
}
