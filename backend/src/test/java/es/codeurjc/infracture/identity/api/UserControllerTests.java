package es.codeurjc.infracture.identity.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.codeurjc.infracture.identity.application.UserAlreadyExistsException;
import es.codeurjc.infracture.identity.application.UserService;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.security.EncoderConfig;
import es.codeurjc.infracture.security.SecurityConfig;
import es.codeurjc.infracture.security.jwt.JwtTokenProvider;
import es.codeurjc.infracture.security.jwt.UnauthorizedHandlerJwt;
import jakarta.servlet.http.Cookie;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, EncoderConfig.class, JwtTokenProvider.class,
        UnauthorizedHandlerJwt.class, UserMapperImpl.class})
class UserControllerTests {

    private static final String REGISTRATION = """
            {"username":"alice","displayName":"Alice","email":"alice@example.com",
             "password":"A secure passphrase 2026!"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void returnsCurrentAccountUsingAuthenticatedEmail() throws Exception {
        User account = new User("alice", "stored-password-hash", "ALICE@EXAMPLE.COM", "Alice", UserRole.USER);
        account.setId(7L);
        var authenticated = org.springframework.security.core.userdetails.User
            .withUsername(account.getEmail()).password(account.getEncodedPassword()).roles("USER").build();
        when(userDetailsService.loadUserByUsername(account.getEmail())).thenReturn(authenticated);
        when(userService.getUserByEmail(account.getEmail())).thenReturn(account);

        mvc.perform(get("/api/v1/users/me")
                .cookie(new Cookie("AuthToken", jwtTokenProvider.generateAccessToken(authenticated))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.email").value("ALICE@EXAMPLE.COM"))
            .andExpect(jsonPath("$.encodedPassword").doesNotExist());

        verify(userService).getUserByEmail(account.getEmail());
    }

    @Test
    void rejectsIdentityRequestsWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void registersWithoutAuthenticationAndReturnsAccountWithoutPassword() throws Exception {
        Instant createdAt = Instant.parse("2026-10-17T10:00:00Z");
        when(userService.registerUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setEncodedPassword("stored-password-hash");
            user.setRole(UserRole.USER);
            user.setActive(true);
            ReflectionTestUtils.setField(user, "createdAt", createdAt);
            ReflectionTestUtils.setField(user, "updatedAt", createdAt);
            return user;
        });

        mvc.perform(registrationRequest())
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/v1/users/1"))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.displayName").value("Alice"))
            .andExpect(jsonPath("$.email").value("alice@example.com"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.createdAt").value(createdAt.toString()))
            .andExpect(jsonPath("$.updatedAt").value(createdAt.toString()))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.encodedPassword").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("registrationFailures")
    void translatesExpectedRegistrationFailures(RuntimeException failure, int expectedStatus) throws Exception {
        when(userService.registerUser(any(User.class))).thenThrow(failure);

        mvc.perform(registrationRequest())
            .andExpect(status().is(expectedStatus))
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(expectedStatus))
            .andExpect(jsonPath("$.detail").value(failure.getMessage()));
    }

    static Stream<Arguments> registrationFailures() {
        return Stream.of(
                Arguments.of(new ConstraintViolationException("Email must be valid", Set.of()), 400),
                Arguments.of(new IllegalArgumentException("Password is required"), 400),
                Arguments.of(new UserAlreadyExistsException("Username is already in use"), 409));
    }

    @Test
    void rejectsRegistrationWithoutCsrfBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
            .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void letsUnexpectedFailuresReachServerErrorHandling() {
        when(userService.registerUser(any(User.class)))
            .thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        assertThatThrownBy(() -> mvc.perform(registrationRequest()))
            .hasRootCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    private MockHttpServletRequestBuilder registrationRequest() throws Exception {
        Cookie token = mvc.perform(get("/api/v1/component-templates"))
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn().getResponse().getCookie("XSRF-TOKEN");

        return post("/api/v1/users").cookie(token).header("X-XSRF-TOKEN", token.getValue())
            .contentType(MediaType.APPLICATION_JSON).content(REGISTRATION);
    }
}
