package es.codeurjc.infracture.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.codeurjc.infracture.identity.api.AuthController;
import es.codeurjc.infracture.security.jwt.JwtTokenProvider;
import es.codeurjc.infracture.security.jwt.UnauthorizedHandlerJwt;
import es.codeurjc.infracture.security.jwt.UserLoginService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest({AuthController.class, SecurityConfigTests.TestController.class})
@Import({
    SecurityConfig.class,
    EncoderConfig.class,
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
    private UserDetailsService userDetailsService;

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
    @ValueSource(strings = {"login", "refresh", "logout"})
    void requiresCsrfProtectionForAuthenticationWrites(String endpoint) throws Exception {
        mvc.perform(post("/api/v1/auth/" + endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@example.com\",\"password\":\"test-password\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void authenticatesWithEncodedPasswordAndIssuedJwtCookie() throws Exception {
        when(userDetailsService.loadUserByUsername("student@example.com"))
            .thenReturn(User.withUsername("student@example.com")
                .password(passwordEncoder.encode("un bosque con café"))
                .roles("USER")
                .build());

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

    @Test
    void rejectsPreviouslyIssuedTokensWhenAccountBecomesInactive() throws Exception {
        var activeUser = User.withUsername("student@example.com")
            .password("stored-password-hash")
            .roles("USER")
            .build();
        String accessToken = jwtTokenProvider.generateAccessToken(activeUser);
        String refreshToken = jwtTokenProvider.generateRefreshToken(activeUser);
        when(userDetailsService.loadUserByUsername(activeUser.getUsername()))
            .thenReturn(User.withUserDetails(activeUser).disabled(true).build());

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
        var user = User.withUsername("student@example.com").password("stored-hash").roles("USER").build();
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);
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
            .andExpect(jsonPath("$.status").value("FAILURE"))
            .andExpect(cookie().doesNotExist("AuthToken"));
    }

    @Test
    void rejectsTokensUsedForTheWrongPurpose() throws Exception {
        var user = User.withUsername("student@example.com").password("stored-hash").roles("USER").build();
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);
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
