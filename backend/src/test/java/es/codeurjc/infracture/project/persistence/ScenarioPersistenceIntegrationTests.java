package es.codeurjc.infracture.project.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.Component;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @Test
    void reopensTheSavedGraphWithPositionsAndParameters() {
        UUID orders = UUID.randomUUID();
        UUID cache = UUID.randomUUID();
        Scenario scenario = repository.saveAndFlush(new Scenario(cacheLab, "Cold cache", null));
        scenario.replaceGraph(1, List.of(
                new ComponentDraft(orders, "http-service", "orders-api", 120.5, -40,
                        new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 250))),
                new ComponentDraft(cache, "redis", "cache", 0, 300,
                        new ComponentConfiguration("DEFAULT", Map.of()))),
                catalogue());
        repository.saveAndFlush(scenario);
        entityManager.clear();

        Scenario reopened = repository.findById(scenario.getId()).orElseThrow();
        assertThat(reopened.getRevision()).isEqualTo(2);
        assertThat(reopened.getComponents()).extracting(Component::getKey).containsExactly(orders, cache);
        Component api = reopened.getComponents().getFirst();
        assertThat(api.getId()).isNotNull();
        assertThat(api.getName()).isEqualTo("orders-api");
        assertThat(api.getType()).isEqualTo(ComponentType.HTTP_SERVICE);
        assertThat(api.getPositionX()).isEqualTo(120.5);
        assertThat(api.getPositionY()).isEqualTo(-40);
        assertThat(api.getConfiguration())
                .isEqualTo(new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 250)));
        assertThat(reopened.getComponents().getLast().getConfiguration())
                .isEqualTo(new ComponentConfiguration("DEFAULT", Map.of()));
    }

    @Test
    void keepsNodeIdentityAndRemovesDeletedNodesOnTheNextSave() {
        UUID kept = UUID.randomUUID();
        Scenario scenario = repository.saveAndFlush(new Scenario(cacheLab, "Cold cache", null));
        scenario.replaceGraph(1, List.of(redis(kept, "cache"), redis(UUID.randomUUID(), "removed")), catalogue());
        repository.saveAndFlush(scenario);
        Long keptId = scenario.getComponents().getFirst().getId();
        entityManager.clear();

        Scenario reopened = repository.findById(scenario.getId()).orElseThrow();
        reopened.replaceGraph(2, List.of(redis(kept, "renamed")), catalogue());
        repository.saveAndFlush(reopened);
        entityManager.clear();

        Scenario again = repository.findById(scenario.getId()).orElseThrow();
        assertThat(again.getRevision()).isEqualTo(3);
        assertThat(again.getComponents()).singleElement()
                .satisfies(component -> assertThat(component.getId()).isEqualTo(keptId))
                .extracting(Component::getName).isEqualTo("renamed");
        assertThat(entityManager.createNativeQuery("SELECT COUNT(*) FROM components").getSingleResult())
                .isEqualTo(1L);
    }

    @Test
    void findsScenarioForUpdateOnlyWithinItsProject() {
        Scenario scenario = repository.saveAndFlush(new Scenario(cacheLab, "Cold cache", null));

        assertThat(repository.findForUpdateByIdAndProjectId(scenario.getId(), cacheLab.getId())).contains(scenario);
        assertThat(repository.findForUpdateByIdAndProjectId(scenario.getId(), queueLab.getId())).isEmpty();
    }

    @Test
    void rejectsRepeatedNodeKeysAndUnknownTemplatesInTheDatabase() {
        Scenario scenario = repository.saveAndFlush(new Scenario(cacheLab, "Cold cache", null));
        String insert = """
                INSERT INTO components (scenario_id, component_key, template_key, type, name,
                                        position_x, position_y, parameters)
                VALUES (:scenario, '3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10', :template, 'REDIS', 'cache',
                        0, 0, '{}')
                """;
        entityManager.createNativeQuery(insert).setParameter("scenario", scenario.getId())
                .setParameter("template", "redis").executeUpdate();

        assertThatThrownBy(() -> entityManager.createNativeQuery(insert).setParameter("scenario", scenario.getId())
                .setParameter("template", "redis").executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("uk_components_scenario_key");
    }

    @Test
    void rejectsNodesOfTemplatesThatDoNotExist() {
        Scenario scenario = repository.saveAndFlush(new Scenario(cacheLab, "Cold cache", null));

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO components (scenario_id, component_key, template_key, type, name,
                                        position_x, position_y, parameters)
                VALUES (:scenario, gen_random_uuid(), 'invented', 'REDIS', 'cache', 0, 0, '{}')
                """).setParameter("scenario", scenario.getId()).executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("fk_components_template");
    }

    @Test
    void deletesTheNodesTogetherWithTheirScenario() {
        Scenario scenario = repository.saveAndFlush(new Scenario(cacheLab, "Cold cache", null));
        scenario.replaceGraph(1, List.of(redis(UUID.randomUUID(), "cache")), catalogue());
        repository.saveAndFlush(scenario);

        entityManager.createNativeQuery("DELETE FROM scenarios WHERE id = :id")
                .setParameter("id", scenario.getId()).executeUpdate();

        assertThat(entityManager.createNativeQuery("SELECT COUNT(*) FROM components").getSingleResult())
                .isEqualTo(0L);
    }

    private static ComponentDraft redis(UUID key, String name) {
        return new ComponentDraft(key, "redis", name, 0, 0, new ComponentConfiguration("DEFAULT", Map.of()));
    }

    private static Map<String, ComponentTemplate> catalogue() {
        return Map.of(
                "http-service", new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE, true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0))),
                "redis", new ComponentTemplate("redis", "Redis", ComponentType.REDIS, true, new ComponentConfiguration("DEFAULT", Map.of())));
    }

    @Test
    void rejectsLoadGeneratorNodesInTheDatabase() {
        Scenario scenario = repository.saveAndFlush(new Scenario(cacheLab, "Cold cache", null));

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO components (scenario_id, component_key, template_key, type, name,
                                        position_x, position_y, parameters)
                VALUES (:scenario, gen_random_uuid(), 'load-generator', 'LOAD_GENERATOR', 'load', 0, 0, '{}')
                """).setParameter("scenario", scenario.getId()).executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("ck_components_type");
    }
}
