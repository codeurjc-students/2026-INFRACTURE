package es.codeurjc.infracture.identity.api;

import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import es.codeurjc.infracture.security.MutableClock;
import es.codeurjc.infracture.security.jwt.TokenType;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource("classpath:admin-test.properties")
@Testcontainers
class AuthenticationApiSystemTests {

    private static final String PASSWORD = "A secure integration password 2026!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository repository;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MutableClock clock;

    @TestConfiguration
    static class ControlledClock {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Test
    void exposesHealthWithoutAuthenticationForStartupScripts() {
        given().port(port).get("/actuator/health")
            .then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void rejectsAnExpiredAccessTokenAndRenewsItWithTheRefreshToken() {
        CookieFilter cookies = new CookieFilter();
        String csrf = csrf(cookies);
        write(cookies, csrf).body(registration("expiryuser", "expiry@example.com"))
            .post("/api/v1/users").then().statusCode(201);
        write(cookies, csrf).body("""
                {"email":"expiry@example.com","password":"%s"}
                """.formatted(PASSWORD)).post("/api/v1/auth/login").then().statusCode(200);
        given().port(port).filter(cookies).get("/api/v1/users/me").then().statusCode(200);

        clock.advance(TokenType.ACCESS.duration.plus(Duration.ofSeconds(1)));
        given().port(port).filter(cookies).get("/api/v1/users/me").then().statusCode(401);

        write(cookies, csrf(cookies)).post("/api/v1/auth/refresh").then().statusCode(200);
        given().port(port).filter(cookies).get("/api/v1/users/me").then().statusCode(200)
            .body("username", equalTo("expiryuser"));
    }

    @Test
    void registersAuthenticatesRefreshesAndLogsOutThroughRealHttp() {
        CookieFilter cookies = new CookieFilter();
        String csrf = csrf(cookies);
        Response registration = write(cookies, csrf).body(registration("flowuser", "FlowUser@example.com"))
            .post("/api/v1/users");
        registration.then().statusCode(201).body("role", equalTo("USER"))
            .body("password", nullValue()).body("encodedPassword", nullValue());
        Long id = registration.jsonPath().getLong("id");
        User saved = repository.findById(id).orElseThrow();
        assertThat(saved.getEncodedPassword()).startsWith("$argon2id$").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getEncodedPassword())).isTrue();
        write(cookies, csrf).body(registration("otherflow", "flowuser@EXAMPLE.COM"))
            .post("/api/v1/users").then().statusCode(409);
        write(cookies, csrf).body("""
                {"email":"FlowUser@example.com","password":"Incorrect password 2026!"}
                """).post("/api/v1/auth/login").then().statusCode(401);
        given().port(port).filter(cookies).get("/api/v1/users/me").then().statusCode(401);

        Response login = write(cookies, csrf).body("""
                {"email":"flowuser@EXAMPLE.COM","password":"%s"}
                """.formatted(PASSWORD)).post("/api/v1/auth/login");
        login.then().statusCode(200);
        assertThat(login.getDetailedCookie("AuthToken").isHttpOnly()).isTrue();
        assertThat(login.getDetailedCookie("RefreshToken").isHttpOnly()).isTrue();
        given().port(port).filter(cookies).get("/api/v1/users/me").then().statusCode(200)
            .body("id", equalTo(id.intValue())).body("email", equalTo("FlowUser@example.com"))
            .body("role", equalTo("USER")).body("password", nullValue()).body("encodedPassword", nullValue());

        csrf = csrf(cookies);
        write(cookies, csrf).post("/api/v1/auth/refresh").then().statusCode(200);
        csrf = csrf(cookies);
        Response logout = write(cookies, csrf).post("/api/v1/auth/logout");
        logout.then().statusCode(204);
        assertThat(logout.getHeaders().getValues("Set-Cookie")).anySatisfy(value ->
            assertThat(value).startsWith("AuthToken=;").contains("Expires=").contains("Path=/"));
        assertThat(logout.getHeaders().getValues("Set-Cookie")).anySatisfy(value ->
            assertThat(value).startsWith("RefreshToken=;").contains("Expires=").contains("Path=/"));
        assertThat(cookies.getCookieStore().getCookies())
            .noneMatch(cookie -> cookie.getName().equals("AuthToken") || cookie.getName().equals("RefreshToken"));
        given().port(port).filter(cookies).get("/api/v1/users/me").then().statusCode(401);
        write(cookies, csrf(cookies)).post("/api/v1/auth/refresh").then().statusCode(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "id", "active", "encodedPassword", "avatarKey", "createdAt", "updatedAt"})
    void rejectsFieldsControlledByTheServerWithoutCreatingAnAccount(String field) {
        CookieFilter cookies = new CookieFilter();
        String username = "tamper_" + field.toLowerCase(Locale.ROOT).substring(0, Math.min(field.length(), 12));
        String body = registration(username, username + "@example.com").stripTrailing();
        body = body.substring(0, body.length() - 1) + ",\"" + field + "\":\"ADMIN\"}";
        write(cookies, csrf(cookies)).body(body).post("/api/v1/users").then().statusCode(400);
        assertThat(repository.existsByUsernameIgnoreCase(username)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "email"})
    void returnsConflictForConcurrentRegistrationsWithTheSameIdentifier(String identifier) throws Exception {
        // Both requests must pass duplicate prechecks before either can insert its user.
        CyclicBarrier beforeInsert = new CyclicBarrier(2);
        doAnswer(invocation -> {
            beforeInsert.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(passwordEncoder).encode(anyString());
        String firstUsername = "race_" + identifier;
        String secondUsername = identifier.equals("username") ? firstUsername.toUpperCase() : "other_race_email";
        String firstEmail = "race_" + identifier + "@example.com";
        String secondEmail = identifier.equals("email") ? firstEmail.toUpperCase() : "other_race_username@example.com";
        CookieFilter firstCookies = new CookieFilter();
        CookieFilter secondCookies = new CookieFilter();
        String firstCsrf = csrf(firstCookies);
        String secondCsrf = csrf(secondCookies);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Response> first = executor.submit(() -> write(firstCookies, firstCsrf)
                .body(registration(firstUsername, firstEmail)).post("/api/v1/users"));
            Future<Response> second = executor.submit(() -> write(secondCookies, secondCsrf)
                .body(registration(secondUsername, secondEmail)).post("/api/v1/users"));
            Response firstResponse = first.get(20, TimeUnit.SECONDS);
            Response secondResponse = second.get(20, TimeUnit.SECONDS);
            assertThat(new int[] {firstResponse.statusCode(), secondResponse.statusCode()})
                .containsExactlyInAnyOrder(201, 409);
            Response conflict = firstResponse.statusCode() == 409 ? firstResponse : secondResponse;
            assertThat(conflict.jsonPath().getString("detail")).isEqualTo(identifier.equals("username")
                ? "Username is already in use" : "Email is already in use");
        }
        assertThat(repository.findAll().stream().filter(user -> identifier.equals("username")
            ? user.getUsername().equalsIgnoreCase(firstUsername)
            : user.getEmail().equalsIgnoreCase(firstEmail))).hasSize(1);
    }

    @Test
    void returnsPasswordPolicyViolationAsProblemDetail() {
        CookieFilter cookies = new CookieFilter();
        String body = registration("weakpassword", "weak@example.com").replace(PASSWORD, "Short 1!");

        write(cookies, csrf(cookies)).body(body).post("/api/v1/users")
            .then().spec(problem(400, "Password must contain between 15 and 128 characters"));
    }

    @Test
    void returnsBadLoginCredentialsAsProblemDetail() {
        CookieFilter cookies = new CookieFilter();
        String csrf = csrf(cookies);
        write(cookies, csrf).body(registration("badlogin", "badlogin@example.com"))
            .post("/api/v1/users").then().statusCode(201);

        write(cookies, csrf).body("""
                {"email":"badlogin@example.com","password":"Incorrect password 2026!"}
                """).post("/api/v1/auth/login")
            .then().spec(problem(401, "Invalid email or password."));
    }

    @Test
    void returnsMissingExpiredOrInvalidAuthenticationAsProblemDetail() {
        CookieFilter cookies = new CookieFilter();
        given().port(port).get("/api/v1/users/me")
            .then().spec(problem(401, "Authentication is required."));

        String csrf = csrf(cookies);
        write(cookies, csrf).body(registration("expireduser", "expired@example.com"))
            .post("/api/v1/users").then().statusCode(201);
        write(cookies, csrf).body("""
                {"email":"expired@example.com","password":"%s"}
                """.formatted(PASSWORD)).post("/api/v1/auth/login").then().statusCode(200);
        clock.advance(TokenType.ACCESS.duration.plus(Duration.ofSeconds(1)));
        given().port(port).filter(cookies).get("/api/v1/users/me")
            .then().spec(problem(401, "Authentication is required."));

        CookieFilter anonymous = new CookieFilter();
        write(anonymous, csrf(anonymous)).post("/api/v1/auth/refresh")
            .then().spec(problem(401, "Authentication is required."));
    }

    @Test
    void returnsMissingCsrfAsProblemDetail() {
        given().port(port).contentType(ContentType.JSON).body(registration("nocsrf", "nocsrf@example.com"))
            .post("/api/v1/users")
            .then().spec(problem(403, "You do not have permission to perform this action."));
        assertThat(repository.existsByUsernameIgnoreCase("nocsrf")).isFalse();
    }

    @Test
    void returnsDuplicateAccountAsProblemDetail() {
        CookieFilter cookies = new CookieFilter();
        String csrf = csrf(cookies);
        write(cookies, csrf).body(registration("duplicate", "duplicate@example.com"))
            .post("/api/v1/users").then().statusCode(201);

        write(cookies, csrf).body(registration("DUPLICATE", "another@example.com"))
            .post("/api/v1/users").then().spec(problem(409, "Username is already in use"));
    }

    private String csrf(CookieFilter cookies) {
        given().port(port).filter(cookies).get("/api/v1/auth/csrf").then().statusCode(204);
        return cookies.getCookieStore().getCookies().stream()
            .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
            .findFirst().orElseThrow().getValue();
    }

    private RequestSpecification write(CookieFilter cookies, String csrf) {
        return given().port(port).filter(cookies).header("X-XSRF-TOKEN", csrf).contentType(ContentType.JSON);
    }

    private String registration(String username, String email) {
        return """
                {"username":"%s","displayName":"Integration User","email":"%s","password":"%s"}
                """.formatted(username, email, PASSWORD);
    }
}
