package es.codeurjc.infracture.identity.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import es.codeurjc.infracture.identity.application.AvatarService;
import es.codeurjc.infracture.identity.application.PasswordPolicyException;
import es.codeurjc.infracture.identity.application.UserAlreadyExistsException;
import es.codeurjc.infracture.identity.application.UserService;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import es.codeurjc.infracture.security.AuthenticatedUser;
import es.codeurjc.infracture.security.EncoderConfig;
import es.codeurjc.infracture.security.RepositoryUserDetailsService;
import es.codeurjc.infracture.security.SecurityConfig;
import es.codeurjc.infracture.security.jwt.JwtTokenProvider;
import es.codeurjc.infracture.security.jwt.UnauthorizedHandlerJwt;
import es.codeurjc.infracture.security.jwt.UserLoginService;
import es.codeurjc.infracture.shared.InvalidDataException;
import jakarta.servlet.http.Cookie;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(UserController.class)
@Import({ SecurityConfig.class, EncoderConfig.class, JwtTokenProvider.class,
        UnauthorizedHandlerJwt.class, UserMapperImpl.class, RepositoryUserDetailsService.class,
        UserLoginService.class })
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
    private UserRepository users;

    @MockitoBean
    private AvatarService avatarService;

    @Test
    void returnsCurrentAccountUsingAuthenticatedEmail() throws Exception {
        User account = new User("alice", "stored-password-hash", "ALICE@EXAMPLE.COM", "Alice", UserRole.USER);
        account.setId(7L);
        account.setAvatarKey("avatars/7/0b5e2c1a.png");
        var authenticated = AuthenticatedUser.from(account);
        when(users.findById(7L)).thenReturn(Optional.of(account));
        when(userService.getUserByEmail(account.getEmail())).thenReturn(account);

        mvc.perform(get("/api/v1/users/me")
                .cookie(new Cookie("AuthToken", jwtTokenProvider.generateAccessToken(authenticated))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("ALICE@EXAMPLE.COM"))
                .andExpect(jsonPath("$.encodedPassword").doesNotExist())
                .andExpect(jsonPath("$.avatarUrl").value("/api/v1/users/me/avatar?v=0b5e2c1a"))
                .andExpect(jsonPath("$.avatarKey").doesNotExist());

        verify(userService).getUserByEmail(account.getEmail());
    }

    @Test
    void changesTheCredentialOfTheSignedInAccountAndRenewsItsCookiesWithoutSecrets() throws Exception {
        User account = signedInAccount();
        when(userService.changePassword(7L, "current secret", "new secret")).thenReturn(account);

        mvc.perform(put("/api/v1/users/me/password").with(csrf())
                .cookie(new Cookie("AuthToken", jwtTokenProvider.generateAccessToken(AuthenticatedUser.from(account))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"current secret\",\"newPassword\":\"new secret\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("AuthToken", true))
                .andExpect(cookie().httpOnly("RefreshToken", true))
                .andExpect(jsonPath("$.encodedPassword").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist())
                .andExpect(content().string(not(containsString("stored-password-hash"))));
    }

    // A 401 would make the web client try to renew a session that is fine.
    @Test
    void reportsAWrongCurrentPasswordWithoutAnAuthenticationError() throws Exception {
        User account = signedInAccount();
        when(userService.changeEmail(7L, "new@example.com", "wrong"))
                .thenThrow(new InvalidDataException("Current password is incorrect"));

        mvc.perform(put("/api/v1/users/me/email").with(csrf())
                .cookie(new Cookie("AuthToken", jwtTokenProvider.generateAccessToken(AuthenticatedUser.from(account))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\",\"currentPassword\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"))
                .andExpect(cookie().doesNotExist("AuthToken"));
    }

    private User signedInAccount() {
        User account = new User("alice", "stored-password-hash", "alice@example.com", "Alice", UserRole.USER);
        account.setId(7L);
        when(users.findById(7L)).thenReturn(Optional.of(account));
        return account;
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
        when(userService.registerUser(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            User user = new User(invocation.getArgument(0), "stored-password-hash", invocation.getArgument(2),
                    invocation.getArgument(1), UserRole.USER);
            user.setId(1L);
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
        when(userService.registerUser(anyString(), anyString(), anyString(), anyString())).thenThrow(failure);

        mvc.perform(registrationRequest())
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(failure.getMessage()));
    }

    static Stream<Arguments> registrationFailures() {
        return Stream.of(
                Arguments.of(new ConstraintViolationException("Email must be valid", Set.of()), 400),
                Arguments.of(new PasswordPolicyException("Password is required"), 400),
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
    void hidesUnexpectedFailuresBehindGenericProblemDetail() throws Exception {
        when(userService.registerUser(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        mvc.perform(registrationRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("Database unavailable"))));
    }

    private MockHttpServletRequestBuilder registrationRequest() throws Exception {
        Cookie token = mvc.perform(get("/api/v1/component-templates"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        return post("/api/v1/users").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(REGISTRATION);
    }
}
