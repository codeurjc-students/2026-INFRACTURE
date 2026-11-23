package es.codeurjc.infracture.identity.api;

import static es.codeurjc.infracture.shared.api.ProblemResponses.problem;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.concurrent.atomic.AtomicInteger;
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
class AccountApiSystemTests {

    private static final String PASSWORD = "A secure integration password 2026!";
    private static final String NEW_PASSWORD = "Another secure password 2027!";
    private static final AtomicInteger ACCOUNTS = new AtomicInteger();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.7-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository repository;

    @Test
    void editsTheProfileAndKeepsEverySession() {
        Account alice = register();
        CookieFilter otherDevice = login(alice.email, PASSWORD);

        write(alice.cookies).body("""
                {"displayName":"  Alice Cooper ","username":" %s "}
                """.formatted(alice.username.toUpperCase() + "_X")).patch("/api/v1/users/me")
                .then().statusCode(200)
                .body("displayName", equalTo("Alice Cooper"))
                .body("username", equalTo(alice.username + "_x"))
                .body("encodedPassword", nullValue()).body("password", nullValue())
                .body("tokenVersion", nullValue());

        User stored = repository.findById(alice.id).orElseThrow();
        assertThat(stored.getDisplayName()).isEqualTo("Alice Cooper");
        assertThat(stored.getTokenVersion()).isZero();
        me(otherDevice).then().statusCode(200).body("displayName", equalTo("Alice Cooper"));
        refresh(otherDevice).then().statusCode(200);
    }

    @Test
    void anEmailChangeEndsOtherSessionsAndKeepsTheOneThatMadeIt() {
        Account alice = register();
        CookieFilter otherDevice = login(alice.email, PASSWORD);
        String newEmail = "changed." + alice.email;

        Response change = write(alice.cookies).body("""
                {"email":"%s","currentPassword":"%s"}
                """.formatted(newEmail, PASSWORD)).put("/api/v1/users/me/email");

        change.then().statusCode(200).body("email", equalTo(newEmail)).body("encodedPassword", nullValue());
        assertThat(change.getDetailedCookie("AuthToken").isHttpOnly()).isTrue();
        assertThat(change.getDetailedCookie("RefreshToken").isHttpOnly()).isTrue();
        assertThat(repository.findById(alice.id).orElseThrow().getTokenVersion()).isEqualTo(1);
        me(alice.cookies).then().statusCode(200).body("email", equalTo(newEmail));
        refresh(alice.cookies).then().statusCode(200);
        me(otherDevice).then().statusCode(401);
        refresh(otherDevice).then().statusCode(401);

        failLogin(alice.email, PASSWORD);
        me(login(newEmail, PASSWORD)).then().statusCode(200).body("id", equalTo(alice.id.intValue()));
    }

    @Test
    void aPasswordChangeEndsOtherSessionsAndKeepsTheOneThatMadeIt() {
        Account alice = register();
        CookieFilter otherDevice = login(alice.email, PASSWORD);

        Response change = write(alice.cookies).body("""
                {"currentPassword":"%s","newPassword":"%s"}
                """.formatted(PASSWORD, NEW_PASSWORD)).put("/api/v1/users/me/password");

        change.then().statusCode(200).body("encodedPassword", nullValue()).body("password", nullValue());
        assertThat(change.asString()).doesNotContain(NEW_PASSWORD);
        assertThat(repository.findById(alice.id).orElseThrow().getTokenVersion()).isEqualTo(1);
        me(alice.cookies).then().statusCode(200);
        refresh(alice.cookies).then().statusCode(200);
        me(otherDevice).then().statusCode(401);
        refresh(otherDevice).then().statusCode(401);

        failLogin(alice.email, PASSWORD);
        me(login(alice.email, NEW_PASSWORD)).then().statusCode(200);
    }

    @Test
    void anOldTokenNeverAuthenticatesAsTheNewOwnerOfAFreedEmail() {
        Account alice = register();
        CookieFilter oldSession = login(alice.email, PASSWORD);
        String freedEmail = alice.email;
        write(alice.cookies).body("""
                {"email":"%s","currentPassword":"%s"}
                """.formatted("moved." + freedEmail, PASSWORD)).put("/api/v1/users/me/email")
                .then().statusCode(200);

        Account newcomer = register("newcomer" + ACCOUNTS.incrementAndGet(), freedEmail);

        assertThat(newcomer.id).isNotEqualTo(alice.id);
        me(oldSession).then().statusCode(401);
        refresh(oldSession).then().statusCode(401);
        me(newcomer.cookies).then().statusCode(200).body("id", equalTo(newcomer.id.intValue()));
    }

    @Test
    void rejectsAWrongCurrentPasswordWithoutEndingTheSession() {
        Account alice = register();

        write(alice.cookies).body("""
                {"email":"other.%s","currentPassword":"Incorrect password 2026!"}
                """.formatted(alice.email)).put("/api/v1/users/me/email")
                .then().spec(problem(400, "Current password is incorrect"));
        write(alice.cookies).body("""
                {"currentPassword":"Incorrect password 2026!","newPassword":"%s"}
                """.formatted(NEW_PASSWORD)).put("/api/v1/users/me/password")
                .then().spec(problem(400, "Current password is incorrect"));

        User stored = repository.findById(alice.id).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo(alice.email);
        assertThat(stored.getTokenVersion()).isZero();
        me(alice.cookies).then().statusCode(200);
    }

    @Test
    void returnsConflictsForIdentifiersOfAnotherAccount() {
        Account alice = register();
        Account bob = register();

        write(alice.cookies).body("""
                {"username":"%s"}
                """.formatted(bob.username.toUpperCase())).patch("/api/v1/users/me")
                .then().spec(problem(409, "Username is already in use"));
        write(alice.cookies).body("""
                {"email":"%s","currentPassword":"%s"}
                """.formatted(bob.email.toUpperCase(), PASSWORD)).put("/api/v1/users/me/email")
                .then().spec(problem(409, "Email is already in use"));

        User stored = repository.findById(alice.id).orElseThrow();
        assertThat(stored.getUsername()).isEqualTo(alice.username);
        assertThat(stored.getEmail()).isEqualTo(alice.email);
        assertThat(stored.getTokenVersion()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "role", "active", "email", "encodedPassword", "tokenVersion", "avatarKey" })
    void rejectsFieldsOutsideTheProfileContract(String field) {
        Account alice = register();

        write(alice.cookies).body("""
                {"displayName":"Tampered","%s":"ADMIN"}
                """.formatted(field)).patch("/api/v1/users/me").then().statusCode(400);

        assertThat(repository.findById(alice.id).orElseThrow().getDisplayName()).isEqualTo("Integration User");
    }

    @Test
    void offersNoOperationToEditAnotherAccount() {
        Account alice = register();
        Account bob = register();

        Response response = write(alice.cookies).body("""
                {"displayName":"Tampered"}
                """).patch("/api/v1/users/" + bob.id);

        assertThat(response.statusCode()).isBetween(400, 499);
        assertThat(repository.findById(bob.id).orElseThrow().getDisplayName()).isEqualTo("Integration User");
    }

    @Test
    void anAdministratorOnlyEditsTheirOwnAccountThroughTheOwnerOperations() {
        Account bob = register();
        CookieFilter admin = login("admin@example.com", "A secure initial admin 2026!");
        int adminId = me(admin).then().statusCode(200).extract().path("id");

        write(admin).body("""
                {"displayName":"Renamed Admin"}
                """).patch("/api/v1/users/me").then().statusCode(200).body("id", equalTo(adminId));
        Response onBob = write(admin).body("""
                {"email":"stolen@example.com","currentPassword":"A secure initial admin 2026!"}
                """).put("/api/v1/users/" + bob.id + "/email");

        assertThat(onBob.statusCode()).isBetween(400, 499);
        assertThat(repository.findById(bob.id).orElseThrow().getEmail()).isEqualTo(bob.email);
    }

    @Test
    void rejectsEditsWithoutASession() {
        CookieFilter anonymous = new CookieFilter();

        write(anonymous).body("{\"displayName\":\"Nobody\"}").patch("/api/v1/users/me")
                .then().spec(problem(401, "Authentication is required."));
    }

    private record Account(Long id, String username, String email, CookieFilter cookies) {
    }

    private Account register() {
        String username = "account" + ACCOUNTS.incrementAndGet();
        return register(username, username + "@example.com");
    }

    private Account register(String username, String email) {
        CookieFilter cookies = new CookieFilter();
        Long id = write(cookies).body("""
                {"username":"%s","displayName":"Integration User","email":"%s","password":"%s"}
                """.formatted(username, email, PASSWORD)).post("/api/v1/users")
                .then().statusCode(201).extract().jsonPath().getLong("id");
        write(cookies).body(credentials(email, PASSWORD)).post("/api/v1/auth/login").then().statusCode(200);
        return new Account(id, username, email, cookies);
    }

    private CookieFilter login(String email, String password) {
        CookieFilter cookies = new CookieFilter();
        write(cookies).body(credentials(email, password)).post("/api/v1/auth/login").then().statusCode(200);
        return cookies;
    }

    private void failLogin(String email, String password) {
        CookieFilter cookies = new CookieFilter();
        write(cookies).body(credentials(email, password)).post("/api/v1/auth/login").then().statusCode(401);
    }

    private static String credentials(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private Response me(CookieFilter cookies) {
        return given().port(port).filter(cookies).get("/api/v1/users/me");
    }

    private Response refresh(CookieFilter cookies) {
        return write(cookies).post("/api/v1/auth/refresh");
    }

    private RequestSpecification write(CookieFilter cookies) {
        given().port(port).filter(cookies).get("/api/v1/auth/csrf").then().statusCode(204);
        String csrf = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .findFirst().orElseThrow().getValue();
        return given().port(port).filter(cookies).header("X-XSRF-TOKEN", csrf).contentType(ContentType.JSON);
    }
}
