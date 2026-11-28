package es.codeurjc.infracture.execution.application;

import static es.codeurjc.infracture.execution.application.PlanCompilerTests.LOAD;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.LOAD_IMAGE;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.POSTGRESQL_IMAGE;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.databaseExecution;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.downstreamExecution;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.execution;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.reference;
import static org.assertj.core.api.Assertions.assertThat;

import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Wiring;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.json.JsonMapper;

// The engine is the only place that writes Compose. The reference files are the Compose plans the backend
// stored before the plan was typed, so the same snapshot still reaches Docker as exactly the same file.
class ComposeRenderTests {

    private final PlanCompiler compiler = new PlanCompiler(List.of(new HttpServiceDriver("infracture/demo-app:0.1.0"),
            new PostgresqlDriver(POSTGRESQL_IMAGE)), "local", LOAD_IMAGE);

    @Test
    void rendersAStatelessServiceAsTheSameComposeFile() throws Exception {
        Map<String, Object> file = ComposeRuntimeEngine.render(compiler.compile(execution(12L)));

        JSONAssert.assertEquals(reference("execution/compose-stateless.json"),
                JsonMapper.builder().build().writeValueAsString(file), JSONCompareMode.STRICT);
    }

    // The load's Compose profile keeps `up --wait` from starting it with the nodes.
    @Test
    void rendersTheLoadAsAK6ServiceThatUpWaitLeavesForLater() throws Exception {
        Map<String, Object> file = ComposeRuntimeEngine.render(compiler.compile(execution(12L, LOAD)));

        JSONAssert.assertEquals(reference("execution/compose-constant-http.json"),
                JsonMapper.builder().build().writeValueAsString(file), JSONCompareMode.STRICT);
    }

    @Test
    void rendersADatabaseThatItsConsumersWaitForWithItsDataInMemory() throws Exception {
        Map<String, Object> file = ComposeRuntimeEngine.render(compiler.compile(databaseExecution(12L)));

        JSONAssert.assertEquals(reference("execution/compose-database-crud.json"),
                JsonMapper.builder().build().writeValueAsString(file), JSONCompareMode.STRICT);
    }

    @Test
    void givesExperimentsNoPortsVolumesPrivilegesOrSecrets() {
        for (ExecutionPlan plan : List.of(compiler.compile(execution(12L, LOAD)),
                compiler.compile(databaseExecution(12L)),
                compiler.compile(downstreamExecution(12L)))) {
            Map<String, Object> file = ComposeRuntimeEngine.render(plan);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> services = (Map<String, Map<String, Object>>) file.get("services");

            assertThat(services).isNotEmpty().allSatisfy((name, service) -> assertThat(service)
                    .doesNotContainKeys("ports", "volumes", "privileged", "secrets", "command", "network_mode"));
            assertThat(file).doesNotContainKeys("volumes", "secrets");
        }
    }

    @Test
    void rendersDownstreamServicesWaitingForTheirSharedTargetHealthAndEscapesNames() {
        ExecutionPlan plan = compiler.compile(downstreamExecution(12L));
        Node gateway = plan.nodes().getFirst();
        Map<String, String> environment = new LinkedHashMap<>(gateway.wiring().environment());
        environment.put("DOWNSTREAM_NAME", "orders-$api");
        Node withDollar = new Node(gateway.key(), gateway.host(), gateway.container(),
                new Wiring(environment, gateway.wiring().secrets(), gateway.wiring().dependencies()), gateway.health(),
                gateway.evidence(), gateway.counters(), gateway.limits());
        ExecutionPlan modified = new ExecutionPlan(plan.project(), plan.labels(),
                List.of(withDollar, plan.nodes().get(1), plan.nodes().get(2)), null);

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> services =
                (Map<String, Map<String, Object>>) ComposeRuntimeEngine.render(modified).get("services");
        String targetHost = plan.nodes().get(2).host();

        assertThat(services).hasSize(3);
        assertThat(services.get(gateway.host()).get("depends_on"))
                .isEqualTo(Map.of(targetHost, Map.of("condition", "service_healthy")));
        assertThat(services.get(plan.nodes().get(1).host()).get("depends_on"))
                .isEqualTo(Map.of(targetHost, Map.of("condition", "service_healthy")));
        @SuppressWarnings("unchecked")
        Map<String, Object> gatewayEnv = (Map<String, Object>) services.get(gateway.host()).get("environment");
        assertThat(gatewayEnv)
                .containsEntry("DOWNSTREAM_URL", "http://" + targetHost + ":8080/")
                .containsEntry("DOWNSTREAM_NAME", "orders-$$api");
    }

    @Test
    void escapesTheUsersTextButNotThePasswordVariable() {
        ExecutionPlan plan = compiler.compile(databaseExecution(12L));
        Node orders = plan.nodes().getFirst();
        Map<String, String> environment = new LinkedHashMap<>(orders.wiring().environment());
        environment.put("COMPONENT_NAME", "${HOME} costs $5");
        Node typed = new Node(orders.key(), orders.host(), orders.container(),
                new Wiring(environment, orders.wiring().secrets(), orders.wiring().dependencies()), orders.health(),
                orders.evidence(), orders.counters(), orders.limits());
        ExecutionPlan withDollars = new ExecutionPlan(plan.project(), plan.labels(),
                List.of(typed, plan.nodes().get(1), plan.nodes().get(2)), null);

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Map<String, Object>>> services =
                (Map<String, Map<String, Map<String, Object>>>) ComposeRuntimeEngine.render(withDollars)
                        .get("services");

        assertThat(services.get(orders.host()).get("environment"))
                .containsEntry("COMPONENT_NAME", "$${HOME} costs $$5")
                .containsEntry("DATABASE_PASSWORD", "${INFRACTURE_SECRET_7c2e4a6b8d0f4b1c9e3a5f7b9d1c3e5a}");
    }
}
