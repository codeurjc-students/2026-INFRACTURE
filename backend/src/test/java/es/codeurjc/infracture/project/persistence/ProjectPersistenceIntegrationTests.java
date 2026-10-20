package es.codeurjc.infracture.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProjectPersistenceIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProjectRepository repository;

    private User alice;
    private User bob;

    @BeforeEach
    void createAccounts() {
        alice = new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER);
        bob = new User("bob", "hash", "bob@example.com", "Bob", UserRole.USER);
        entityManager.persist(alice);
        entityManager.persist(bob);
    }

    @Test
    void persistsOwnerDetailsAndTimestampsAcrossReopening() {
        Project project = repository.saveAndFlush(new Project(alice, "Cache lab", "Redis in front of PostgreSQL"));
        entityManager.clear();

        Project saved = repository.findById(project.getId()).orElseThrow();
        assertThat(saved.getOwner().getId()).isEqualTo(alice.getId());
        assertThat(saved.getName()).isEqualTo("Cache lab");
        assertThat(saved.getDescription()).isEqualTo("Redis in front of PostgreSQL");
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());

        Instant createdAt = saved.getCreatedAt();
        Instant beforeUpdate = Instant.now().truncatedTo(ChronoUnit.MICROS);
        saved.changeDetails("Renamed lab", null);
        entityManager.flush();
        entityManager.clear();

        Project updated = repository.findById(project.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Renamed lab");
        assertThat(updated.getDescription()).isNull();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    void pagesOnlyOwnProjectsNewestFirst() {
        for (int number = 1; number <= 11; number++) {
            repository.save(new Project(alice, "Alice lab " + number, null));
        }
        repository.save(new Project(bob, "Bob lab", null));
        repository.flush();

        Page<Project> first = repository.findAllByOwnerIdOrderByCreatedAtDescIdDesc(
                alice.getId(), PageRequest.of(0, 10));
        Page<Project> second = repository.findAllByOwnerIdOrderByCreatedAtDescIdDesc(
                alice.getId(), PageRequest.of(1, 10));

        assertThat(first.getTotalElements()).isEqualTo(11);
        assertThat(first.getContent()).hasSize(10).first().extracting(Project::getName).isEqualTo("Alice lab 11");
        assertThat(second.getContent()).extracting(Project::getName).containsExactly("Alice lab 1");
        assertThat(first.getContent()).extracting(project -> project.getOwner().getId()).containsOnly(alice.getId());
    }

    @Test
    void findsProjectByIdOnlyForItsOwner() {
        Project project = repository.saveAndFlush(new Project(alice, "Private lab", null));

        assertThat(repository.findByIdAndOwnerId(project.getId(), alice.getId())).contains(project);
        assertThat(repository.findByIdAndOwnerId(project.getId(), bob.getId())).isEmpty();
    }

    @Test
    void rejectsProjectsWithoutExistingOwner() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO projects (owner_id, name, created_at, updated_at)
                VALUES (-1, 'Orphan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("fk_projects_owner");
    }

    @Test
    void rejectsProjectsWithBlankName() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO projects (owner_id, name, created_at, updated_at)
                VALUES (:owner, '   ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).setParameter("owner", alice.getId()).executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("ck_projects_name_not_blank");
    }
}
