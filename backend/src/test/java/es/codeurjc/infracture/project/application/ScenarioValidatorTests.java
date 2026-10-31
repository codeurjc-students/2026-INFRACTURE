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
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import es.codeurjc.infracture.project.domain.ValidationIssue.Kind;
import es.codeurjc.infracture.project.domain.ValidationIssue.Severity;
import es.codeurjc.infracture.project.domain.ValidationIssue.Target;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioValidatorTests {

    private static final UUID ORDERS = UUID.randomUUID();
    private static final UUID CACHE = UUID.randomUUID();

    private final ScenarioValidator validator = new ScenarioValidator(ContractCatalogs.standard());
    private final Scenario scenario = new Scenario(new Project(
            new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER), "Cache lab", null),
            "Cold cache", null);
    private final Map<String, ComponentTemplate> templates = Map.of(
            "http-service", new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE, true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0))),
            "redis", new ComponentTemplate("redis", "Redis", ComponentType.REDIS, true, new ComponentConfiguration("DEFAULT", Map.of())));

    @Test
    void findsNoIssuesInAGraphThatFollowsItsContracts() {
        scenario.replaceGraph(1, List.of(
                node(ORDERS, "http-service", "orders-api", "STATELESS", Map.of("responseDelayMs", 250)),
                node(CACHE, "redis", "cache", "DEFAULT", Map.of())), templates);

        assertThat(validator.validate(scenario)).isEmpty();
    }

    @Test
    void reportsContractProblemsAsErrorsOnTheNodeAndField() {
        scenario.replaceGraph(1, List.of(
                node(ORDERS, "http-service", "orders-api", "STATELESS", Map.of("responseDelayMs", 9000)),
                node(CACHE, "redis", "cache", "CACHE_ASIDE", Map.of())), templates);

        assertThat(validator.validate(scenario)).containsExactly(
                new ValidationIssue(Severity.ERROR, "PARAMETER_OUT_OF_RANGE",
                        "responseDelayMs must be between 0 and 5000",
                        new Target(Kind.COMPONENT, ORDERS.toString()), "responseDelayMs"),
                new ValidationIssue(Severity.ERROR, "UNKNOWN_PROFILE", "REDIS has no profile CACHE_ASIDE",
                        new Target(Kind.COMPONENT, CACHE.toString()), "profile"));
    }

    @Test
    void reportsATypeWithoutContractInsteadOfFailing() {
        ContractCatalog empty = org.mockito.Mockito.mock(ContractCatalog.class);
        org.mockito.Mockito.when(empty.find(ComponentType.REDIS)).thenReturn(java.util.Optional.empty());
        scenario.replaceGraph(1, List.of(node(CACHE, "redis", "cache", "DEFAULT", Map.of())), templates);

        assertThat(new ScenarioValidator(empty).validate(scenario)).containsExactly(
                new ValidationIssue(Severity.ERROR, "UNKNOWN_TYPE", "REDIS has no contract",
                        new Target(Kind.COMPONENT, CACHE.toString()), null));
    }

    private static ComponentDraft node(UUID key, String template, String name, String profile,
            Map<String, Integer> parameters) {
        return new ComponentDraft(key, template, name, 0, 0, new ComponentConfiguration(profile, parameters));
    }
}
