package es.codeurjc.infracture.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.catalog.domain.Capability;
import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.domain.ContractViolation;
import es.codeurjc.infracture.catalog.domain.PortContract;
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "HTTP_SERVICE | STATELESS       | ''                                                | HTTP",
            "HTTP_SERVICE | DATABASE_CRUD   | database:SQL_DATABASE:1:1                         | HTTP RESOURCE_READ RESOURCE_WRITE",
            "HTTP_SERVICE | CACHE_ASIDE     | cache:KEY_VALUE:1:1 database:SQL_DATABASE:1:1     | HTTP RESOURCE_READ RESOURCE_WRITE",
            "HTTP_SERVICE | QUEUE_PRODUCER  | queue:MESSAGE_BROKER:1:1                          | HTTP TASK_CREATE",
            "HTTP_SERVICE | DOWNSTREAM_HTTP | downstream:HTTP:1:1                               | HTTP",
            "WORKER       | CONSUMER        | queue:MESSAGE_BROKER:1:1 result:SQL_DATABASE,KEY_VALUE:0:1 | ''",
            "POSTGRESQL   | DEFAULT         | ''                                                | SQL_DATABASE",
            "REDIS        | DEFAULT         | ''                                                | KEY_VALUE",
            "RABBITMQ     | DEFAULT         | ''                                                | MESSAGE_BROKER"})
    void declaresThePortsEachProfileNeedsAndTheCapabilitiesItOffers(ComponentType type, String name, String ports,
            String capabilities) {
        ProfileContract profile = catalog.find(type).orElseThrow().profile(name).orElseThrow();

        assertThat(profile.ports()).map(port -> "%s:%s:%d:%d".formatted(port.name(),
                        String.join(",", port.accepts().stream().map(Capability::name).toList()), port.min(), port.max()))
                .containsExactly(ports.isEmpty() ? new String[0] : ports.split(" +"));
        assertThat(profile.ports()).allSatisfy(port -> assertThat(port.label()).isNotBlank());
        assertThat(profile.capabilities()).map(Capability::name)
                .containsExactly(capabilities.isEmpty() ? new String[0] : capabilities.split(" "));
    }

    @Test
    void labelsMessagingPortsWithTheDirectionOfTheMessages() {
        assertThat(port(ComponentType.HTTP_SERVICE, "QUEUE_PRODUCER", "queue").label()).isEqualTo("Publishes to");
        assertThat(port(ComponentType.WORKER, "CONSUMER", "queue").label()).isEqualTo("Consumes from");
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

    private PortContract port(ComponentType type, String profile, String name) {
        return catalog.find(type).orElseThrow().profile(profile).orElseThrow().ports().stream()
                .filter(port -> port.name().equals(name)).findFirst().orElseThrow();
    }

    private List<ContractViolation> check(ComponentType type, String profile, Map<String, Integer> parameters) {
        return catalog.find(type).orElseThrow().check(new ComponentConfiguration(profile, parameters));
    }
}
