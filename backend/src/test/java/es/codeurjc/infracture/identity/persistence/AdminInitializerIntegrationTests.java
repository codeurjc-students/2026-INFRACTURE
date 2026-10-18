package es.codeurjc.infracture.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.identity.application.AdminInitializer;
import es.codeurjc.infracture.identity.application.UserService;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@TestPropertySource("classpath:admin-test.properties")
@Testcontainers
class AdminInitializerIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    private UserRepository repository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAdministratorOnStartupAndDoesNotResetCredentialsWhenRunAgain() {
        User initial = repository.findByEmailIgnoreCase("admin@example.com").orElseThrow();
        String initialHash = initial.getEncodedPassword();
        Long initialId = initial.getId();
        assertThat(initial.getUsername()).isEqualTo("infracture_admin");
        assertThat(initial.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(passwordEncoder.matches("A secure initial admin 2026!", initialHash)).isTrue();

        new AdminInitializer(userService, new MockEnvironment()).run(new DefaultApplicationArguments());

        User existing = repository.findByEmailIgnoreCase("admin@example.com").orElseThrow();
        assertThat(existing.getId()).isEqualTo(initialId);
        assertThat(existing.getEncodedPassword()).isEqualTo(initialHash);
        assertThat(repository.findAll().stream().filter(user -> user.getRole() == UserRole.ADMIN)).hasSize(1);
    }

    @Test
    void databaseRejectsSecondAdministratorButRegistrationStillCreatesOrdinaryAccounts() {
        assertThatThrownBy(() -> repository.saveAndFlush(
                new User("otheradmin", "hash", "otheradmin@example.com", "Other Admin", UserRole.ADMIN)))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uk_users_single_admin");

        User ordinary = userService.registerUser(new User("ordinary", "A secure user password 2026!",
                "ordinary@example.com", "Ordinary User", UserRole.ADMIN));

        assertThat(ordinary.getRole()).isEqualTo(UserRole.USER);
        assertThat(repository.existsByRole(UserRole.ADMIN)).isTrue();
    }
}
