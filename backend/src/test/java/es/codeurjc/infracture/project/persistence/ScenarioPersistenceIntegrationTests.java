package es.codeurjc.infracture.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
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
class ScenarioPersistenceIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ScenarioRepository repository;

    private Project cacheLab;
    private Project queueLab;

    @BeforeEach
    void createProjects() {
        User alice = new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER);
        entityManager.persist(alice);
        cacheLab = new Project(alice, "Cache lab", null);
        queueLab = new Project(alice, "Queue lab", null);
        entityManager.persist(cacheLab);
        entityManager.persist(queueLab);
    }

    @Test
    void persistsMembershipDetailsAndInitialRevisionAcrossReopening() {
        Scenario scenario = repository.saveAndFlush(new Scenario(cacheLab, "Cold cache", "Empty Redis"));
        repository.saveAndFlush(new Scenario(cacheLab, "Warm cache", null));
        entityManager.clear();
        Instant projectUpdatedAt = entityManager.find(Project.class, cacheLab.getId()).getUpdatedAt();

        Scenario saved = repository.findById(scenario.getId()).orElseThrow();
        assertThat(saved.getProject().getId()).isEqualTo(cacheLab.getId());
        assertThat(saved.getName()).isEqualTo("Cold cache");
        assertThat(saved.getDescription()).isEqualTo("Empty Redis");
        assertThat(saved.getRevision()).isEqualTo(1);

        saved.changeDetails("Renamed", null);
        entityManager.flush();
        entityManager.clear();

        Scenario updated = repository.findById(scenario.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getDescription()).isNull();
        assertThat(updated.getRevision()).isEqualTo(1);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
        assertThat(repository.findAll()).extracting(Scenario::getName).containsExactlyInAnyOrder("Renamed", "Warm cache");
        assertThat(entityManager.find(Project.class, cacheLab.getId()).getUpdatedAt()).isEqualTo(projectUpdatedAt);
    }

    @Test
    void pagesOnlyScenariosOfTheProjectNewestFirst() {
        for (int number = 1; number <= 11; number++) {
            repository.save(new Scenario(cacheLab, "Cache " + number, null));
        }
        repository.save(new Scenario(queueLab, "Queue", null));
        repository.flush();

        Page<Scenario> first = repository.findAllByProjectIdOrderByCreatedAtDescIdDesc(
                cacheLab.getId(), PageRequest.of(0, 10));
        Page<Scenario> second = repository.findAllByProjectIdOrderByCreatedAtDescIdDesc(
                cacheLab.getId(), PageRequest.of(1, 10));

        assertThat(first.getTotalElements()).isEqualTo(11);
        assertThat(first.getContent()).hasSize(10).first().extracting(Scenario::getName).isEqualTo("Cache 11");
        assertThat(second.getContent()).extracting(Scenario::getName).containsExactly("Cache 1");
    }

    @Test
    void ordersScenariosCreatedAtTheSameInstantByNewestId() {
        entityManager.createNativeQuery("""
                INSERT INTO scenarios (project_id, name, revision, created_at, updated_at)
                VALUES (:project, 'First', 1, TIMESTAMP '2026-10-21 10:00:00', TIMESTAMP '2026-10-21 10:00:00'),
                       (:project, 'Second', 1, TIMESTAMP '2026-10-21 10:00:00', TIMESTAMP '2026-10-21 10:00:00')
                """).setParameter("project", cacheLab.getId()).executeUpdate();

        assertThat(repository.findAllByProjectIdOrderByCreatedAtDescIdDesc(cacheLab.getId(), PageRequest.of(0, 10)))
                .extracting(Scenario::getName).containsExactly("Second", "First");
    }

    @Test
    void findsScenarioByIdOnlyWithinItsProject() {
        Scenario scenario = repository.saveAndFlush(new Scenario(cacheLab, "Cold cache", null));

        assertThat(repository.findByIdAndProjectId(scenario.getId(), cacheLab.getId())).contains(scenario);
        assertThat(repository.findByIdAndProjectId(scenario.getId(), queueLab.getId())).isEmpty();
    }

    @Test
    void rejectsScenariosWithoutExistingProject() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO scenarios (project_id, name, revision, created_at, updated_at)
                VALUES (-1, 'Orphan', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("fk_scenarios_project");
    }

    @Test
    void rejectsRevisionsBelowTheInitialOne() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO scenarios (project_id, name, revision, created_at, updated_at)
                VALUES (:project, 'Lab', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).setParameter("project", cacheLab.getId()).executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("ck_scenarios_revision_positive");
    }
}
