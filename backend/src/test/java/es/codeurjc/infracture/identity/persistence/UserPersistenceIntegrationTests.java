package es.codeurjc.infracture.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserPersistenceIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository repository;

    @Test
    void generatesIdentityAndMaintainsTimestampsWhenSavingUser() {
        User user = new User("alice", "encoded-password", "Alice@Example.com", "Alice", UserRole.USER);
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        User saved = entityManager.find(User.class, user.getId());
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getEmail()).isEqualTo("Alice@Example.com");
        assertThat(repository.findByEmailIgnoreCase("aLiCe@eXaMpLe.CoM")).contains(saved);
        assertThat(repository.existsByEmailIgnoreCase("ALICE@EXAMPLE.COM")).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getAvatarKey()).isNull();

        Instant createdAt = saved.getCreatedAt();
        Instant beforeUpdate = Instant.now().truncatedTo(ChronoUnit.MICROS);
        saved.setDisplayName("Alice Updated");
        entityManager.flush();
        entityManager.clear();

        User updated = entityManager.find(User.class, user.getId());
        assertThat(updated.getDisplayName()).isEqualTo("Alice Updated");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @ParameterizedTest
    @CsvSource({
            "' ALICE ', other@example.com, uk_users_username_normalized",
            "other, ' ALICE@EXAMPLE.COM ', uk_users_email_normalized"
    })
    void rejectsDuplicateNormalizedIdentifiers(String username, String email, String constraint) {
        entityManager.persist(new User("alice", "encoded-password", "alice@example.com", "Alice", UserRole.USER));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("""
                    INSERT INTO users (username, encoded_password, email, display_name, role, created_at, updated_at)
                    VALUES (:username, 'encoded-password', :email, 'Another Alice', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)
                    .setParameter("username", username)
                    .setParameter("email", email)
                    .executeUpdate();
        }).isInstanceOf(PersistenceException.class).hasMessageContaining(constraint);
    }

    @Test
    void rejectsRolesOutsideUserRole() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO users (username, encoded_password, email, display_name, role, created_at, updated_at)
                VALUES ('alice', 'encoded-password', 'alice@example.com', 'Alice', 'OWNER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("ck_users_role");
    }
}
