package es.codeurjc.infracture.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.codeurjc.infracture.identity.api.AuthController;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import es.codeurjc.infracture.security.jwt.JwtTokenProvider;
import es.codeurjc.infracture.security.jwt.UnauthorizedHandlerJwt;
import es.codeurjc.infracture.security.jwt.UserLoginService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest({ AuthController.class, SecurityConfigTests.TestController.class })
@Import({
        SecurityConfig.class,
        EncoderConfig.class,
        RepositoryUserDetailsService.class,
        JwtTokenProvider.class,
        UnauthorizedHandlerJwt.class,
        UserLoginService.class,
        AuthController.class,
        SecurityConfigTests.TestController.class
})
class SecurityConfigTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository users;

    private User storedAccount(String encodedPassword) {
        User account = new User("student", encodedPassword, "student@example.com", "Student", UserRole.USER);
        account.setId(7L);
        when(users.findByEmailIgnoreCase("student@example.com")).thenReturn(Optional.of(account));
        when(users.findById(7L)).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void keepsCataloguePublic() throws Exception {
        mvc.perform(get("/api/v1/component-templates"))
                .andExpect(status().isOk())
                .andExpect(content().string("catalogue"));
    }

    @Test
    void rejectsPrivateRequestsWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/security-test/private"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidAccessToken() throws Exception {
        mvc.perform(get("/api/v1/security-test/private")
                .cookie(new Cookie("AuthToken", "invalid-token")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = { "login", "refresh", "logout" })
    void requiresCsrfProtectionForAuthenticationWrites(String endpoint) throws Exception {
        mvc.perform(post("/api/v1/auth/" + endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatesWithEncodedPasswordAndIssuedJwtCookie() throws Exception {
        storedAccount(passwordEncoder.encode("un bosque con café"));

        Cookie csrfCookie = csrfCookie();

        var response = mvc.perform(post("/api/v1/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@example.com\",\"password\":\"un bosque con cafe\u0301\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AuthToken"))
                .andExpect(cookie().exists("RefreshToken"))
                .andReturn()
                .getResponse();

        mvc.perform(get("/api/v1/security-test/private")
                .cookie(response.getCookie("AuthToken")))
                .andExpect(status().isOk())
                .andExpect(content().string("private"));
    }

    // A password change that commits during the login must also end the session of that login.
    @Test
    void issuesTheLoginTokenWithTheVersionThatWasCheckedWithThePassword() throws Exception {
        User checked = storedAccount(passwordEncoder.encode("un bosque con café"));
        User changedMeanwhile = new User("student", "new-hash", "student@example.com", "Student", UserRole.USER);
        changedMeanwhile.setId(7L);
        changedMeanwhile.revokeSessions();
        when(users.findByEmailIgnoreCase("student@example.com"))
                .thenReturn(Optional.of(checked), Optional.of(changedMeanwhile));
        when(users.findById(7L)).thenReturn(Optional.of(changedMeanwhile));
        Cookie csrfCookie = csrfCookie();

        var response = mvc.perform(post("/api/v1/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@example.com\",\"password\":\"un bosque con café\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        mvc.perform(get("/api/v1/security-test/private").cookie(response.getCookie("AuthToken")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsLoginOfInactiveAccountWithoutRevealingAccountState() throws Exception {
        storedAccount(passwordEncoder.encode("un bosque con café")).setActive(false);
        Cookie csrfCookie = csrfCookie();

        mvc.perform(post("/api/v1/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@example.com\",\"password\":\"un bosque con café\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."))
                .andExpect(cookie().doesNotExist("AuthToken"));
    }

    @Test
    void rejectsPreviouslyIssuedTokensWhenAccountBecomesInactive() throws Exception {
        User account = storedAccount("stored-password-hash");
        var activeUser = AuthenticatedUser.from(account);
        String accessToken = jwtTokenProvider.generateAccessToken(activeUser);
        String refreshToken = jwtTokenProvider.generateRefreshToken(activeUser);
        account.setActive(false);

        mvc.perform(get("/api/v1/security-test/private")
                .cookie(new Cookie("AuthToken", accessToken)))
                .andExpect(status().isUnauthorized());

        Cookie csrfCookie = csrfCookie();
        mvc.perform(post("/api/v1/auth/refresh")
                .cookie(csrfCookie, new Cookie("RefreshToken", refreshToken))
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("AuthToken"));
    }

    @Test
    void rejectsPreviouslyIssuedTokensWhenTheVersionChanges() throws Exception {
        User account = storedAccount("stored-password-hash");
        var previous = AuthenticatedUser.from(account);
        String accessToken = jwtTokenProvider.generateAccessToken(previous);
        String refreshToken = jwtTokenProvider.generateRefreshToken(previous);
        account.revokeSessions();

        mvc.perform(get("/api/v1/security-test/private")
                .cookie(new Cookie("AuthToken", accessToken)))
                .andExpect(status().isUnauthorized());

        Cookie csrfCookie = csrfCookie();
        mvc.perform(post("/api/v1/auth/refresh")
                .cookie(csrfCookie, new Cookie("RefreshToken", refreshToken))
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("AuthToken"));
    }

    @Test
    void refreshesAccessWithoutAnAccessCookie() throws Exception {
        var user = AuthenticatedUser.from(storedAccount("stored-hash"));
        Cookie csrfCookie = csrfCookie();

        mvc.perform(post("/api/v1/auth/refresh")
                .cookie(csrfCookie, new Cookie("RefreshToken", jwtTokenProvider.generateRefreshToken(user)))
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(cookie().httpOnly("AuthToken", true));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "invalid-token")
    void rejectsMissingOrInvalidRefreshCookies(String refreshToken) throws Exception {
        Cookie csrfCookie = csrfCookie();
        var request = post("/api/v1/auth/refresh").cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue());
        if (refreshToken != null) {
            request.cookie(new Cookie("RefreshToken", refreshToken));
        }

        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Authentication is required."))
                .andExpect(cookie().doesNotExist("AuthToken"));
    }

    @Test
    void rejectsTokensUsedForTheWrongPurpose() throws Exception {
        var user = AuthenticatedUser.from(storedAccount("stored-hash"));
        Cookie csrfCookie = csrfCookie();

        mvc.perform(post("/api/v1/auth/refresh")
                .cookie(csrfCookie, new Cookie("RefreshToken", jwtTokenProvider.generateAccessToken(user)))
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("AuthToken"));

        mvc.perform(get("/api/v1/security-test/private")
                .cookie(new Cookie("AuthToken", jwtTokenProvider.generateRefreshToken(user))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clearsAuthenticationCookiesEvenWhenAccessTokenIsInvalid() throws Exception {
        Cookie csrfCookie = csrfCookie();

        mvc.perform(post("/api/v1/auth/logout")
                .cookie(csrfCookie, new Cookie("AuthToken", "invalid-token"))
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("AuthToken", 0))
                .andExpect(cookie().maxAge("RefreshToken", 0))
                .andExpect(cookie().path("AuthToken", "/"))
                .andExpect(cookie().path("RefreshToken", "/"));
    }

    private Cookie csrfCookie() throws Exception {
        return mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/component-templates")
        String catalogue() {
            return "catalogue";
        }

        @GetMapping("/api/v1/security-test/private")
        String privateResource() {
            return "private";
        }

    }
}
