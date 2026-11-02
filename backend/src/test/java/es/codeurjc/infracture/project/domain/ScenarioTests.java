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
    private static final UUID THIRD = UUID.fromString("9c8b7a6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");

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
        scenario.replaceGraph(1, List.of(), List.of(), templates);

        assertThat(scenario.getComponents()).isEmpty();
        assertThat(scenario.getRevision()).isEqualTo(2);
    }

    @Test
    void createsIndependentNodesFromTheSameTemplate() {
        scenario.replaceGraph(1, List.of(
                httpNode(FIRST, "orders-api", 0),
                httpNode(SECOND, "users-api", 250)), List.of(), templates);

        assertThat(scenario.getComponents()).hasSize(2);
        Component orders = component(FIRST);
        Component users = component(SECOND);
        assertThat(orders.getTemplateKey()).isEqualTo("http-service");
        assertThat(orders.getType()).isEqualTo(ComponentType.HTTP_SERVICE);
        assertThat(orders.getConfiguration()).isEqualTo(stateless(0));
        assertThat(users.getConfiguration()).isEqualTo(stateless(250));

        scenario.replaceGraph(2, List.of(
                httpNode(FIRST, "orders-api", 900),
                httpNode(SECOND, "users-api", 250)), List.of(), templates);

        assertThat(component(FIRST).getConfiguration()).isEqualTo(stateless(900));
        assertThat(component(SECOND).getConfiguration()).isEqualTo(stateless(250));
        assertThat(scenario.getRevision()).isEqualTo(3);
    }

    @Test
    void updatesNodesByKeyAndRemovesTheMissingOnes() {
        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0), redisNode(SECOND)), List.of(), templates);
        Component orders = component(FIRST);

        scenario.replaceGraph(2, List.of(new ComponentDraft(FIRST, "http-service", "orders", 300.5, -20,
                stateless(100))), List.of(), templates);

        assertThat(scenario.getComponents()).containsExactly(orders);
        assertThat(orders.getName()).isEqualTo("orders");
        assertThat(orders.getPositionX()).isEqualTo(300.5);
        assertThat(orders.getPositionY()).isEqualTo(-20);
        assertThat(orders.getConfiguration()).isEqualTo(stateless(100));
    }

    @Test
    void keepsSavedNodesWhoseTemplateWasDisabledButRejectsNewOnes() {
        Map<String, ComponentTemplate> before = Map.of("redis", redis);
        scenario.replaceGraph(1, List.of(redisNode(FIRST)), List.of(), before);

        scenario.replaceGraph(2, List.of(redisNode(FIRST)), List.of(), Map.of());
        assertThat(scenario.getRevision()).isEqualTo(3);

        assertThatThrownBy(() -> scenario.replaceGraph(3, List.of(redisNode(FIRST),
                new ComponentDraft(SECOND, "old-worker", "worker", 0, 0, empty())), List.of(), templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Component worker: unknown or disabled template old-worker");
    }

    @Test
    void rejectsAStaleRevisionWithoutChangingTheGraph() {
        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0)), List.of(), templates);

        assertThatThrownBy(() -> scenario.replaceGraph(1, List.of(), List.of(), templates))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The scenario was saved after revision 1. Reload it to continue from revision 2.");
        assertThat(scenario.getComponents()).hasSize(1);
        assertThat(scenario.getRevision()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidNodesListingEveryProblemWithoutChangingTheGraph() {
        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0)), List.of(), templates);

        assertThatThrownBy(() -> scenario.replaceGraph(2, List.of(
                new ComponentDraft(FIRST, "redis", "orders-api", 0, 0, empty()),
                new ComponentDraft(SECOND, "http-service", " ", 0, 0, stateless(9000)),
                new ComponentDraft(null, "unknown", "x".repeat(31), 0, 0, empty())), List.of(), templates))
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
                        new ComponentConfiguration("CACHE_ASIDE", Map.of("maxmemory", 64)))), List.of(), templates);

        assertThat(scenario.getRevision()).isEqualTo(2);
        assertThat(component(FIRST).getConfiguration()).isEqualTo(stateless(9000));
        assertThat(component(SECOND).getConfiguration().profile()).isEqualTo("CACHE_ASIDE");
    }

    @Test
    void rejectsProfileNamesLongerThanTheDatabaseAccepts() {
        assertThatThrownBy(() -> scenario.replaceGraph(1, List.of(new ComponentDraft(FIRST, "redis", "cache", 0, 0,
                new ComponentConfiguration("P".repeat(51), Map.of()))), List.of(), templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Component cache: profile must contain at most 50 characters");
    }

    @Test
    void rejectsRepeatedKeys() {
        assertThatThrownBy(() -> scenario.replaceGraph(1, List.of(redisNode(FIRST), redisNode(FIRST)), List.of(), templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Component " + FIRST + " appears more than once");
    }

    @Test
    void acceptsUpToFiftyNodes() {
        List<ComponentDraft> fifty = new ArrayList<>(IntStream.range(0, 50)
                .mapToObj(number -> redisNode(UUID.randomUUID())).toList());
        scenario.replaceGraph(1, fifty, List.of(), templates);
        assertThat(scenario.getComponents()).hasSize(50);

        fifty.add(redisNode(UUID.randomUUID()));
        assertThatThrownBy(() -> scenario.replaceGraph(2, fifty, List.of(), templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("A scenario can have at most 50 components");
    }

    @Test
    void savesConnectionsBetweenItsNodesIncludingSharedDependencies() {
        Connection orders = new Connection(FIRST, "cache", THIRD);
        Connection users = new Connection(SECOND, "cache", THIRD);

        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0), httpNode(SECOND, "users-api", 0),
                redisNode(THIRD)), List.of(orders, users), templates);

        assertThat(scenario.getConnections()).containsExactly(orders, users);
    }

    @Test
    void listsOnlyTheConnectionsThatLeaveANode() {
        Connection cache = new Connection(FIRST, "cache", THIRD);
        Connection database = new Connection(FIRST, "database", SECOND);
        Connection users = new Connection(SECOND, "cache", THIRD);

        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0), httpNode(SECOND, "users-api", 0),
                redisNode(THIRD)), List.of(cache, users, database), templates);

        assertThat(scenario.connectionsFrom(FIRST)).containsExactly(cache, database);
        assertThat(scenario.connectionsFrom(THIRD)).isEmpty();
    }

    @Test
    void keepsConnectionsTheContractDoesNotAllowAsADraft() {
        Connection unknownPort = new Connection(FIRST, "database", SECOND);

        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0), redisNode(SECOND)),
                List.of(unknownPort), templates);

        assertThat(scenario.getConnections()).containsExactly(unknownPort);
    }

    @Test
    void removesANodeTogetherWithItsConnections() {
        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0), redisNode(SECOND)),
                List.of(new Connection(FIRST, "cache", SECOND)), templates);

        scenario.replaceGraph(2, List.of(httpNode(FIRST, "orders-api", 0)), List.of(), templates);

        assertThat(scenario.getComponents()).extracting(Component::getKey).containsExactly(FIRST);
        assertThat(scenario.getConnections()).isEmpty();
    }

    @Test
    void rejectsConnectionsThatLeaveTheGraphListingEveryProblemWithoutChangingIt() {
        Connection saved = new Connection(FIRST, "cache", SECOND);
        scenario.replaceGraph(1, List.of(httpNode(FIRST, "orders-api", 0), redisNode(SECOND)), List.of(saved),
                templates);
        UUID outside = UUID.fromString("00000000-0000-4000-8000-000000000001");

        assertThatThrownBy(() -> scenario.replaceGraph(2, List.of(httpNode(FIRST, "orders-api", 0)), List.of(
                saved,
                new Connection(outside, "cache", FIRST),
                new Connection(FIRST, " ", FIRST),
                new Connection(FIRST, "p".repeat(51), FIRST),
                new Connection(FIRST, "downstream", FIRST),
                new Connection(FIRST, "downstream", FIRST)), templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage(String.join("; ",
                        "Connection orders-api.cache → " + SECOND + ": target is not a node of this scenario",
                        "Connection " + outside + ".cache → orders-api: source is not a node of this scenario",
                        "Connection orders-api.  → orders-api: port is required",
                        "Connection orders-api." + "p".repeat(51)
                                + " → orders-api: port must contain at most 50 characters",
                        "Connection orders-api.downstream → orders-api: appears more than once"));
        assertThat(scenario.getConnections()).containsExactly(saved);
        assertThat(scenario.getRevision()).isEqualTo(2);
    }

    @Test
    void acceptsUpToTwoHundredConnections() {
        List<ComponentDraft> nodes = List.of(httpNode(FIRST, "orders-api", 0), redisNode(SECOND));
        List<Connection> connections = new ArrayList<>(IntStream.range(0, 200)
                .mapToObj(number -> new Connection(FIRST, "port" + number, SECOND)).toList());
        scenario.replaceGraph(1, nodes, connections, templates);
        assertThat(scenario.getConnections()).hasSize(200);

        connections.add(new Connection(FIRST, "one-more", SECOND));
        assertThatThrownBy(() -> scenario.replaceGraph(2, nodes, connections, templates))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("A scenario can have at most 200 connections");
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
