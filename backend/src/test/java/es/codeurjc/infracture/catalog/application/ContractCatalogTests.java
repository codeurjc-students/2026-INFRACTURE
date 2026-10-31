package es.codeurjc.infracture.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.domain.ContractViolation;
import es.codeurjc.infracture.catalog.domain.ProfileContract;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContractCatalogTests {

    private final ContractCatalog catalog = ContractCatalogs.standard();

    @ParameterizedTest
    @CsvSource({"HTTP_SERVICE, STATELESS", "WORKER, CONSUMER", "POSTGRESQL, DEFAULT", "REDIS, DEFAULT",
            "RABBITMQ, DEFAULT"})
    void givesEveryNodeTypeAtLeastOneProfile(ComponentType type, String profile) {
        TemplateContract contract = catalog.find(type).orElseThrow();

        assertThat(contract.version()).isEqualTo(1);
        assertThat(contract.profiles()).extracting(ProfileContract::name).contains(profile);
    }

    @Test
    void refusesToStartWhenATypeHasTwoDefinitions() {
        assertThatThrownBy(() -> new ContractCatalog(List.of(new HttpServiceDefinition(), new HttpServiceDefinition(),
                new WorkerDefinition(), new PostgresqlDefinition(), new RedisDefinition(), new RabbitmqDefinition())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("HTTP_SERVICE has more than one definition");
    }

    @Test
    void refusesToStartWhenATypeHasNoDefinition() {
        assertThatThrownBy(() -> new ContractCatalog(List.of(new HttpServiceDefinition(), new WorkerDefinition(),
                new PostgresqlDefinition(), new RedisDefinition())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RABBITMQ has no definition");
    }

    @Test
    void acceptsConfigurationsInsideTheLimits() {
        assertThat(check(ComponentType.HTTP_SERVICE, "STATELESS", Map.of("responseDelayMs", 0))).isEmpty();
        assertThat(check(ComponentType.HTTP_SERVICE, "STATELESS", Map.of("responseDelayMs", 5000))).isEmpty();
        assertThat(check(ComponentType.REDIS, "DEFAULT", Map.of())).isEmpty();
    }

    @Test
    void reportsEachProblemWithACodeAndTheFieldItConcerns() {
        assertThat(check(ComponentType.HTTP_SERVICE, "STATELESS", Map.of("responseDelayMs", 5001)))
                .containsExactly(new ContractViolation("PARAMETER_OUT_OF_RANGE", "responseDelayMs",
                        "responseDelayMs must be between 0 and 5000"));
        assertThat(check(ComponentType.HTTP_SERVICE, "STATELESS", Map.of("image", 1)))
                .extracting(ContractViolation::code, ContractViolation::field)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MISSING_PARAMETER", "responseDelayMs"),
                        org.assertj.core.groups.Tuple.tuple("UNEXPECTED_PARAMETER", "image"));
        assertThat(check(ComponentType.REDIS, "CACHE_ASIDE", Map.of()))
                .extracting(ContractViolation::code).containsExactly("UNKNOWN_PROFILE");
        assertThat(check(ComponentType.REDIS, null, Map.of()))
                .extracting(ContractViolation::code).containsExactly("MISSING_PROFILE");
    }

    private List<ContractViolation> check(ComponentType type, String profile, Map<String, Integer> parameters) {
        return catalog.find(type).orElseThrow().check(new ComponentConfiguration(profile, parameters));
    }
}
