package es.codeurjc.infracture.project.application;

import static org.assertj.core.api.Assertions.assertThat;

import es.codeurjc.infracture.catalog.application.ContractCatalog;
import es.codeurjc.infracture.catalog.application.ContractCatalogs;
import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Connection;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import es.codeurjc.infracture.project.domain.ValidationIssue.Kind;
import es.codeurjc.infracture.project.domain.ValidationIssue.Severity;
import es.codeurjc.infracture.project.domain.ValidationIssue.Target;
import es.codeurjc.infracture.project.domain.Workload;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioValidatorTests {

    private static final UUID ORDERS = UUID.randomUUID();
    private static final UUID CACHE = UUID.randomUUID();
    private static final UUID DATABASE = UUID.randomUUID();
    private static final UUID WORKER = UUID.randomUUID();
    private static final UUID BROKER = UUID.randomUUID();
    private static final UUID GATEWAY = UUID.randomUUID();
    private static final UUID PRODUCER = UUID.randomUUID();
    private static final Target LOAD = new Target(Kind.WORKLOAD, null);

    private final ScenarioValidator validator = new ScenarioValidator(ContractCatalogs.standard());
    private final Scenario scenario = new Scenario(new Project(
            new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER), "Cache lab", null),
            "Cold cache", null);
    private final Map<String, ComponentTemplate> templates = Map.of(
            "http-service", new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE, true,
                    new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0))),
            "redis", new ComponentTemplate("redis", "Redis", ComponentType.REDIS, true, new ComponentConfiguration("DEFAULT", Map.of())),
            "postgresql", new ComponentTemplate("postgresql", "PostgreSQL", ComponentType.POSTGRESQL, true,
                    new ComponentConfiguration("DEFAULT", Map.of())),
            "rabbitmq", new ComponentTemplate("rabbitmq", "RabbitMQ", ComponentType.RABBITMQ, true,
                    new ComponentConfiguration("DEFAULT", Map.of())),
            "worker", new ComponentTemplate("worker", "Worker", ComponentType.WORKER, true,
                    new ComponentConfiguration("CONSUMER", Map.of())));

    @Test
    void findsNoIssuesInAGraphThatFollowsItsContracts() {
        scenario.replaceGraph(1, List.of(
                node(ORDERS, "http-service", "orders-api", "STATELESS", Map.of("responseDelayMs", 250)),
                node(CACHE, "redis", "cache", "DEFAULT", Map.of())), List.of(), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).isEmpty();
    }

    @Test
    void reportsContractProblemsAsErrorsOnTheNodeAndField() {
        scenario.replaceGraph(1, List.of(
                node(ORDERS, "http-service", "orders-api", "STATELESS", Map.of("responseDelayMs", 9000)),
                node(CACHE, "redis", "cache", "CACHE_ASIDE", Map.of())), List.of(), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).containsExactly(
                new ValidationIssue(Severity.ERROR, "PARAMETER_OUT_OF_RANGE",
                        "responseDelayMs must be between 0 and 5000",
                        new Target(Kind.COMPONENT, ORDERS.toString()), "responseDelayMs"),
                new ValidationIssue(Severity.ERROR, "UNKNOWN_PROFILE", "REDIS has no profile CACHE_ASIDE",
                        new Target(Kind.COMPONENT, CACHE.toString()), "profile"));
    }

    @Test
    void acceptsConnectionsWhoseTargetOffersWhatThePortNeeds() {
        scenario.replaceGraph(1, List.of(
                node(ORDERS, "http-service", "orders-api", "CACHE_ASIDE", Map.of("responseDelayMs", 0)),
                node(CACHE, "redis", "cache", "DEFAULT", Map.of()),
                node(DATABASE, "postgresql", "orders-db", "DEFAULT", Map.of()),
                node(BROKER, "rabbitmq", "tasks", "DEFAULT", Map.of()),
                node(WORKER, "worker", "worker", "CONSUMER", Map.of())), List.of(
                new Connection(ORDERS, "cache", CACHE),
                new Connection(ORDERS, "database", DATABASE),
                new Connection(WORKER, "queue", BROKER),
                new Connection(WORKER, "result", DATABASE)), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).isEmpty();
    }

    @Test
    void reportsConnectionsThatTheirProfileNoLongerAllowsAsErrors() {
        scenario.replaceGraph(1, List.of(
                node(ORDERS, "http-service", "orders-api", "STATELESS", Map.of("responseDelayMs", 0)),
                node(WORKER, "worker", "worker", "CONSUMER", Map.of()),
                node(CACHE, "redis", "cache", "DEFAULT", Map.of())), List.of(
                new Connection(ORDERS, "cache", CACHE),
                new Connection(WORKER, "queue", CACHE)), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).containsExactly(
                new ValidationIssue(Severity.ERROR, "UNKNOWN_PORT", "orders-api has no port cache in profile STATELESS",
                        new Target(Kind.CONNECTION, ORDERS + ":cache:" + CACHE), "cache"),
                new ValidationIssue(Severity.ERROR, "CAPABILITY_MISMATCH",
                        "Port queue of worker needs MESSAGE_BROKER, which cache does not offer",
                        new Target(Kind.CONNECTION, WORKER + ":queue:" + CACHE), "queue"));
    }

    @Test
    void leavesConnectionsOfAnUnknownProfileToTheProfileIssue() {
        scenario.replaceGraph(1, List.of(
                node(ORDERS, "http-service", "orders-api", "LEGACY", Map.of()),
                node(CACHE, "redis", "cache", "LEGACY", Map.of()),
                node(WORKER, "worker", "worker", "CONSUMER", Map.of())), List.of(
                new Connection(ORDERS, "cache", CACHE),
                new Connection(WORKER, "result", CACHE)), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).extracting(ValidationIssue::code)
                .containsExactly("UNKNOWN_PROFILE", "UNKNOWN_PROFILE", "MISSING_CONNECTION");
    }

    @Test
    void reportsPortsWithFewerConnectionsThanTheirMinimumOnTheNode() {
        scenario.replaceGraph(1, List.of(
                node(ORDERS, "http-service", "orders-api", "DATABASE_CRUD", Map.of("responseDelayMs", 0)),
                node(WORKER, "worker", "worker", "CONSUMER", Map.of())), List.of(), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).containsExactly(
                new ValidationIssue(Severity.ERROR, "MISSING_CONNECTION",
                        "orders-api needs at least 1 connection on port database",
                        new Target(Kind.COMPONENT, ORDERS.toString()), "database"),
                new ValidationIssue(Severity.ERROR, "MISSING_CONNECTION",
                        "worker needs at least 1 connection on port queue",
                        new Target(Kind.COMPONENT, WORKER.toString()), "queue"));
    }

    @Test
    void reportsPortsWithMoreConnectionsThanTheirMaximumOnTheNode() {
        scenario.replaceGraph(1, List.of(
                node(WORKER, "worker", "worker", "CONSUMER", Map.of()),
                node(BROKER, "rabbitmq", "tasks", "DEFAULT", Map.of()),
                node(DATABASE, "postgresql", "orders-db", "DEFAULT", Map.of()),
                node(CACHE, "redis", "cache", "DEFAULT", Map.of())), List.of(
                new Connection(WORKER, "queue", BROKER),
                new Connection(WORKER, "result", DATABASE),
                new Connection(WORKER, "result", CACHE)), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).containsExactly(
                new ValidationIssue(Severity.ERROR, "TOO_MANY_CONNECTIONS",
                        "worker accepts at most 1 connection on port result",
                        new Target(Kind.COMPONENT, WORKER.toString()), "result"));
    }

    @Test
    void reportsTheConnectionThatClosesEachCycleIncludingSelfCalls() {
        scenario.replaceGraph(1, List.of(
                node(GATEWAY, "http-service", "gateway", "DOWNSTREAM_HTTP", Map.of("responseDelayMs", 0)),
                node(ORDERS, "http-service", "orders-api", "DOWNSTREAM_HTTP", Map.of("responseDelayMs", 0)),
                node(CACHE, "http-service", "echo", "DOWNSTREAM_HTTP", Map.of("responseDelayMs", 0))), List.of(
                new Connection(GATEWAY, "downstream", ORDERS),
                new Connection(ORDERS, "downstream", GATEWAY),
                new Connection(CACHE, "downstream", CACHE)), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).containsExactly(
                new ValidationIssue(Severity.ERROR, "DEPENDENCY_CYCLE", "orders-api → gateway closes a dependency cycle",
                        new Target(Kind.CONNECTION, ORDERS + ":downstream:" + GATEWAY), "downstream"),
                new ValidationIssue(Severity.ERROR, "DEPENDENCY_CYCLE", "echo → echo closes a dependency cycle",
                        new Target(Kind.CONNECTION, CACHE + ":downstream:" + CACHE), "downstream"));
    }

    @Test
    void acceptsAcyclicChainsAndSharedDependencies() {
        scenario.replaceGraph(1, List.of(
                node(GATEWAY, "http-service", "gateway", "DOWNSTREAM_HTTP", Map.of("responseDelayMs", 0)),
                node(ORDERS, "http-service", "orders-api", "CACHE_ASIDE", Map.of("responseDelayMs", 0)),
                node(CACHE, "redis", "cache", "DEFAULT", Map.of()),
                node(DATABASE, "postgresql", "orders-db", "DEFAULT", Map.of()),
                node(PRODUCER, "http-service", "tasks-api", "QUEUE_PRODUCER", Map.of("responseDelayMs", 0)),
                node(BROKER, "rabbitmq", "tasks", "DEFAULT", Map.of()),
                node(WORKER, "worker", "worker", "CONSUMER", Map.of())), List.of(
                new Connection(GATEWAY, "downstream", ORDERS),
                new Connection(ORDERS, "cache", CACHE),
                new Connection(ORDERS, "database", DATABASE),
                new Connection(PRODUCER, "queue", BROKER),
                new Connection(WORKER, "queue", BROKER),
                new Connection(WORKER, "result", DATABASE)), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).isEmpty();
    }

    @Test
    void reportsAnEmptyScenarioAsAnErrorOnTheScenario() {
        assertThat(withoutLoad(validator.validate(scenario))).containsExactly(
                new ValidationIssue(Severity.ERROR, "EMPTY_SCENARIO", "Add at least one component to run the scenario",
                        new Target(Kind.SCENARIO, null), null));
    }

    @Test
    void warnsWhenTasksReachABrokerThatNoWorkerConsumes() {
        scenario.replaceGraph(1, List.of(
                node(PRODUCER, "http-service", "tasks-api", "QUEUE_PRODUCER", Map.of("responseDelayMs", 0)),
                node(BROKER, "rabbitmq", "tasks", "DEFAULT", Map.of())), List.of(
                new Connection(PRODUCER, "queue", BROKER)), null, templates);

        assertThat(withoutLoad(validator.validate(scenario))).containsExactly(
                new ValidationIssue(Severity.WARNING, "NO_CONSUMER",
                        "Tasks published to tasks accumulate because no Worker consumes them",
                        new Target(Kind.COMPONENT, BROKER.toString()), null));
    }

    @Test
    void reportsATypeWithoutContractInsteadOfFailing() {
        ContractCatalog empty = org.mockito.Mockito.mock(ContractCatalog.class);
        org.mockito.Mockito.when(empty.find(ComponentType.REDIS)).thenReturn(java.util.Optional.empty());
        scenario.replaceGraph(1, List.of(node(CACHE, "redis", "cache", "DEFAULT", Map.of())), List.of(), null, templates);

        assertThat(withoutLoad(new ScenarioValidator(empty).validate(scenario))).containsExactly(
                new ValidationIssue(Severity.ERROR, "UNKNOWN_TYPE", "REDIS has no contract",
                        new Target(Kind.COMPONENT, CACHE.toString()), null));
    }

    @Test
    void warnsThatAScenarioWithoutLoadReceivesNoTraffic() {
        scenario.replaceGraph(1, List.of(stateless(ORDERS)), List.of(), null, templates);

        assertThat(validator.validate(scenario)).containsExactly(
                new ValidationIssue(Severity.WARNING, "NO_WORKLOAD",
                        "The scenario has no load, so the experiment receives no traffic", LOAD, null));
    }

    @Test
    void acceptsAConstantHttpLoadOnANodeThatOffersHttp() {
        scenario.replaceGraph(1, List.of(stateless(ORDERS)), List.of(),
                load(ORDERS, "CONSTANT_HTTP", constantHttp(10, 5, 60), 42), templates);

        assertThat(validator.validate(scenario)).isEmpty();
    }

    @Test
    void reportsLoadParametersAndSeedOutsideTheirLimitsOnTheLoad() {
        scenario.replaceGraph(1, List.of(stateless(ORDERS)), List.of(), load(ORDERS, "CONSTANT_HTTP",
                Map.of("requestsPerSecond", 0, "virtualUsers", 21, "payloadBytes", 10), -1), templates);

        assertThat(validator.validate(scenario)).containsExactly(
                new ValidationIssue(Severity.ERROR, "PARAMETER_OUT_OF_RANGE",
                        "requestsPerSecond must be between 1 and 50", LOAD, "requestsPerSecond"),
                new ValidationIssue(Severity.ERROR, "PARAMETER_OUT_OF_RANGE",
                        "virtualUsers must be between 1 and 20", LOAD, "virtualUsers"),
                new ValidationIssue(Severity.ERROR, "MISSING_PARAMETER", "durationSeconds is required", LOAD,
                        "durationSeconds"),
                new ValidationIssue(Severity.ERROR, "UNEXPECTED_PARAMETER", "CONSTANT_HTTP does not accept payloadBytes",
                        LOAD, "payloadBytes"),
                new ValidationIssue(Severity.ERROR, "PARAMETER_OUT_OF_RANGE", "seed must be between 0 and 2147483647",
                        LOAD, "seed"));
    }

    @Test
    void reportsALoadWithoutTargetAndKeepsItsSettings() {
        scenario.replaceGraph(1, List.of(stateless(ORDERS)), List.of(),
                load(null, "CONSTANT_HTTP", constantHttp(10, 5, 60), 42), templates);

        assertThat(validator.validate(scenario)).containsExactly(
                new ValidationIssue(Severity.ERROR, "WORKLOAD_WITHOUT_TARGET", "Choose the node that receives the load",
                        LOAD, "targetKey"));
        assertThat(scenario.getWorkload()).hasValue(load(null, "CONSTANT_HTTP", constantHttp(10, 5, 60), 42));
    }

    @Test
    void reportsALoadWhoseTargetDoesNotOfferWhatTheProfileNeeds() {
        scenario.replaceGraph(1, List.of(node(CACHE, "redis", "cache", "DEFAULT", Map.of())), List.of(),
                load(CACHE, "CONSTANT_HTTP", constantHttp(10, 5, 60), 42), templates);

        assertThat(validator.validate(scenario)).containsExactly(
                new ValidationIssue(Severity.ERROR, "CAPABILITY_MISMATCH",
                        "The load CONSTANT_HTTP needs HTTP, which cache does not offer", LOAD, "targetKey"));
    }

    @Test
    void reportsAMissingOrUnknownLoadProfileWithoutJudgingItsParameters() {
        scenario.replaceGraph(1, List.of(stateless(ORDERS)), List.of(), load(ORDERS, null, Map.of(), 42), templates);

        assertThat(validator.validate(scenario)).containsExactly(
                new ValidationIssue(Severity.ERROR, "MISSING_PROFILE", "The load needs a profile", LOAD, "profile"));

        scenario.replaceGraph(2, List.of(stateless(ORDERS)), List.of(),
                load(ORDERS, "REPEATED_READ", Map.of(), 42), templates);

        assertThat(validator.validate(scenario)).containsExactly(
                new ValidationIssue(Severity.ERROR, "UNKNOWN_PROFILE", "There is no load profile REPEATED_READ",
                        LOAD, "profile"));
    }

    // The graph tests leave the load out; the load tests check its issues.
    private static List<ValidationIssue> withoutLoad(List<ValidationIssue> issues) {
        return issues.stream().filter(issue -> issue.target().kind() != Kind.WORKLOAD).toList();
    }

    private static ComponentDraft stateless(UUID key) {
        return node(key, "http-service", "orders-api", "STATELESS", Map.of("responseDelayMs", 0));
    }

    private static Workload load(UUID target, String profile, Map<String, Integer> parameters, int seed) {
        return new Workload(target, profile, parameters, seed);
    }

    private static Map<String, Integer> constantHttp(int requestsPerSecond, int virtualUsers, int durationSeconds) {
        return Map.of("requestsPerSecond", requestsPerSecond, "virtualUsers", virtualUsers, "durationSeconds",
                durationSeconds);
    }

    private static ComponentDraft node(UUID key, String template, String name, String profile,
            Map<String, Integer> parameters) {
        return new ComponentDraft(key, template, name, 0, 0, new ComponentConfiguration(profile, parameters));
    }
}
