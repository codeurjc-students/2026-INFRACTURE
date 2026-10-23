package es.codeurjc.infracture.project.api;

import static io.restassured.RestAssured.given;

import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.concurrent.atomic.AtomicInteger;

final class ApiSession {

    private static final String PASSWORD = "A secure integration password 2026!";
    private static final AtomicInteger ACCOUNTS = new AtomicInteger();

    private final int port;
    private final CookieFilter cookies = new CookieFilter();

    private ApiSession(int port) {
        this.port = port;
    }

    static ApiSession signUp(int port) {
        String username = "projects_user_" + ACCOUNTS.incrementAndGet();
        String email = username + "@example.com";
        ApiSession session = new ApiSession(port);
        session.write().body("""
                {"username":"%s","displayName":"Project User","email":"%s","password":"%s"}
                """.formatted(username, email, PASSWORD)).post("/api/v1/users").then().statusCode(201);
        session.write().body("""
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD)).post("/api/v1/auth/login").then().statusCode(200);
        return session;
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
