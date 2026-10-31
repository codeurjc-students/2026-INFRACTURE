package es.codeurjc.infracture.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.shared.ConflictException;
import es.codeurjc.infracture.shared.InvalidDataException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ScenarioTests {

    private static final UUID FIRST = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final UUID SECOND = UUID.fromString("7a1d2c3b-4e5f-4a6b-8c9d-0e1f2a3b4c5d");

    private final ComponentTemplate http = new ComponentTemplate("http-service", "HTTP Service",
            ComponentType.HTTP_SERVICE, true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)));
    private final ComponentTemplate redis = new ComponentTemplate("redis", "Redis", ComponentType.REDIS, true, new ComponentConfiguration("DEFAULT", Map.of()));
    private final ComponentTemplate disabled = new ComponentTemplate("old-worker", "Old worker",
            ComponentType.WORKER, false, new ComponentConfiguration("CONSUMER", Map.of()));
    private final Map<String, ComponentTemplate> templates = Map.of(
            "http-service", http, "redis", redis, "old-worker", disabled);
    private final Scenario scenario = new Scenario(new Project(
            new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER), "Cache lab", null),
            "Cold cache", null);

    @Test
    void savesAnEmptyGraphAndIncrementsTheRevision() {
        scenario.replaceGraph(1, List.of(), templates);

        assertThat(scenario.getComponents()).isEmpty();
        assertThat(scenario.getRevision()).isEqualTo(2);
    }

    @Test
    void createsIndependentNodesFromTheSameTemplate() {
        scenario.replaceGraph(1, List.of(
                httpNode(FIRST, "orders-api", 0),
                httpNode(SECOND, "users-api", 250)), templates);

        assertThat(scenario.getComponents()).hasSize(2);
        Component orders = component(FIRST);
        Component users = component(SECOND);
        assertThat(orders.getTemplateKey()).isEqualTo("http-service");
        assertThat(orders.getType()).isEqualTo(ComponentType.HTTP_SERVICE);
        assertThat(orders.getConfiguration()).isEqualTo(stateless(0));
        assertThat(users.getConfiguration()).isEqualTo(stateless(250));

        scenario.replaceGraph(2, List.of(
                httpNode(FIRST, "orders-api", 900),
                httpNode(SECOND, "users-api", 250)), templates);

        assertThat(component(FIRST).getConfiguration()).isEqualTo(stateless(900));
        assertThat(component(SECOND).getConfiguration()).isEqualTo(stateless(250));
        assertThat(scenario.getRevision()).isEqualTo(3);
    }

    @Test
    void updatesNodesByKeyAndRemovesTheMissingOnes() {
        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0), redisNode(SECOND)), templates);
        Component orders = component(FIRST);

        scenario.replaceGraph(2, List.of(new ComponentDraft(FIRST, "http-service", "orders", 300.5, -20,
                stateless(100))), templates);

        assertThat(scenario.getComponents()).containsExactly(orders);
        assertThat(orders.getName()).isEqualTo("orders");
        assertThat(orders.getPositionX()).isEqualTo(300.5);
        assertThat(orders.getPositionY()).isEqualTo(-20);
        assertThat(orders.getConfiguration()).isEqualTo(stateless(100));
    }

    @Test
    void keepsSavedNodesWhoseTemplateWasDisabledButRejectsNewOnes() {
        Map<String, ComponentTemplate> before = Map.of("redis", redis);
        scenario.replaceGraph(1, List.of(redisNode(FIRST)), before);

        scenario.replaceGraph(2, List.of(redisNode(FIRST)), Map.of());
        assertThat(scenario.getRevision()).isEqualTo(3);

        assertThatThrownBy(() -> scenario.replaceGraph(3, List.of(redisNode(FIRST),
                new ComponentDraft(SECOND, "old-worker", "worker", 0, 0, empty())), templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Component worker: unknown or disabled template old-worker");
    }

    @Test
    void rejectsAStaleRevisionWithoutChangingTheGraph() {
        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0)), templates);

        assertThatThrownBy(() -> scenario.replaceGraph(1, List.of(), templates))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The scenario was saved after revision 1. Reload it to continue from revision 2.");
        assertThat(scenario.getComponents()).hasSize(1);
        assertThat(scenario.getRevision()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidNodesListingEveryProblemWithoutChangingTheGraph() {
        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0)), templates);

        assertThatThrownBy(() -> scenario.replaceGraph(2, List.of(
                new ComponentDraft(FIRST, "redis", "orders-api", 0, 0, empty()),
                new ComponentDraft(SECOND, "http-service", " ", 0, 0, stateless(9000)),
                new ComponentDraft(null, "unknown", "x".repeat(31), 0, 0, empty())), templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage(String.join("; ",
                        "Component orders-api: template cannot change from http-service to redis",
                        "Component " + SECOND + ": name is required",
                        "Component " + "x".repeat(31) + ": key is required",
                        "Component " + "x".repeat(31) + ": name must contain at most 30 characters",
                        "Component " + "x".repeat(31) + ": unknown or disabled template unknown"));
        assertThat(component(FIRST).getConfiguration()).isEqualTo(stateless(0));
        assertThat(scenario.getRevision()).isEqualTo(2);
    }

    @Test
    void savesConfigurationOutsideTheContractAsADraft() {
        scenario.replaceGraph(1, List.of(
                httpNode(FIRST, "orders-api", 9000),
                new ComponentDraft(SECOND, "redis", "cache", 0, 0,
                        new ComponentConfiguration("CACHE_ASIDE", Map.of("maxmemory", 64)))), templates);

        assertThat(scenario.getRevision()).isEqualTo(2);
        assertThat(component(FIRST).getConfiguration()).isEqualTo(stateless(9000));
        assertThat(component(SECOND).getConfiguration().profile()).isEqualTo("CACHE_ASIDE");
    }

    @Test
    void rejectsProfileNamesLongerThanTheDatabaseAccepts() {
        assertThatThrownBy(() -> scenario.replaceGraph(1, List.of(new ComponentDraft(FIRST, "redis", "cache", 0, 0,
                new ComponentConfiguration("P".repeat(51), Map.of()))), templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Component cache: profile must contain at most 50 characters");
    }

    @Test
    void rejectsRepeatedKeys() {
        assertThatThrownBy(() -> scenario.replaceGraph(1, List.of(redisNode(FIRST), redisNode(FIRST)), templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Component " + FIRST + " appears more than once");
    }

    @Test
    void acceptsUpToFiftyNodes() {
        List<ComponentDraft> fifty = new ArrayList<>(IntStream.range(0, 50)
                .mapToObj(number -> redisNode(UUID.randomUUID())).toList());
        scenario.replaceGraph(1, fifty, templates);
        assertThat(scenario.getComponents()).hasSize(50);

        fifty.add(redisNode(UUID.randomUUID()));
        assertThatThrownBy(() -> scenario.replaceGraph(2, fifty, templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("A scenario can have at most 50 components");
    }

    private Component component(UUID key) {
        return scenario.getComponents().stream().filter(c -> c.getKey().equals(key)).findFirst().orElseThrow();
    }

    private static ComponentDraft httpNode(UUID key, String name, int delay) {
        return new ComponentDraft(key, "http-service", name, 120, 80, stateless(delay));
    }

    private static ComponentDraft redisNode(UUID key) {
        return new ComponentDraft(key, "redis", "cache", 0, 0, new ComponentConfiguration("DEFAULT", Map.of()));
    }

    private static ComponentConfiguration stateless(int delay) {
        return new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", delay));
    }

    private static ComponentConfiguration empty() {
        return new ComponentConfiguration(null, Map.of());
    }
}
