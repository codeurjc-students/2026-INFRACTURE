package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.Execution;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.ComponentDraft;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.domain.Workload;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class PlanCompilerTests {

    private static final UUID ORDERS = UUID.fromString("3f0c8a1e-5b7d-4c2a-9f1e-2d6b8a4c7e10");
    private static final String LOAD_IMAGE = "infracture/load-generator:0.1.0";
    private static final Workload LOAD = new Workload(ORDERS, "CONSTANT_HTTP",
            Map.of("requestsPerSecond", 10, "virtualUsers", 5, "durationSeconds", 60), 42);

    private final PlanCompiler compiler = new PlanCompiler(
            List.of(new HttpServiceDriver("infracture/demo-app:0.1.0")), "local", LOAD_IMAGE);

    // Reference file: given this snapshot, exactly this plan.
    @Test
    void compilesAStatelessServiceIntoTheReferencePlan() throws Exception {
        Map<String, Object> plan = compiler.compile(execution(12L));

        JSONAssert.assertEquals(reference("execution/stateless-plan.json"),
                JsonMapper.builder().build().writeValueAsString(plan), JSONCompareMode.STRICT);
    }

    // The load only reaches the plan as numbers and the target URL; k6 chooses its fixed script (ADR 0012).
    @Test
    void addsTheLoadAsAK6ServiceThatUpWaitLeavesForLater() throws Exception {
        Map<String, Object> plan = compiler.compile(execution(12L, LOAD));

        JSONAssert.assertEquals(reference("execution/constant-http-plan.json"),
                JsonMapper.builder().build().writeValueAsString(plan), JSONCompareMode.STRICT);
    }

    @Test
    void givesExperimentsNoPortsVolumesPrivilegesOrSecrets() {
        Map<String, Object> plan = compiler.compile(execution(12L, LOAD));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> services = (Map<String, Map<String, Object>>) plan.get("services");

        assertThat(services).hasSize(2).allSatisfy((name, service) -> assertThat(service)
                .doesNotContainKeys("ports", "volumes", "privileged", "secrets", "command", "network_mode"));
        assertThat(plan).doesNotContainKeys("volumes", "secrets");
    }

    @Test
    void refusesANodeTypeWithoutADriver() {
        PlanCompiler withoutDrivers = new PlanCompiler(List.of(), "local", LOAD_IMAGE);

        assertThatIllegalStateException().isThrownBy(() -> withoutDrivers.compile(execution(12L)))
                .withMessage("No driver runs HTTP_SERVICE");
    }

    @Test
    void namesTheComposeProjectAfterTheInstanceSoInstancesSharingDockerStayApart() {
        PlanCompiler tests = new PlanCompiler(List.of(), "test-1a2b3c4d", LOAD_IMAGE);

        assertThat(compiler.projectName(12L)).isEqualTo("infracture-local-exec-12");
        assertThat(tests.projectName(12L)).isEqualTo("infracture-test-1a2b3c4d-exec-12");
    }

    @Test
    void refusesAnInstanceIdThatComposeCannotUse() {
        assertThatIllegalStateException().isThrownBy(() -> new PlanCompiler(List.of(), "My Mac", LOAD_IMAGE));
        assertThatIllegalStateException().isThrownBy(() -> new PlanCompiler(List.of(), "", LOAD_IMAGE));
    }

    @Test
    void refusesTwoDriversForTheSameType() {
        HttpServiceDriver driver = new HttpServiceDriver("image");

        assertThatIllegalStateException().isThrownBy(() -> new PlanCompiler(List.of(driver, driver), "local", LOAD_IMAGE));
    }

    private static Execution execution(Long id) {
        return execution(id, null);
    }

    private static Execution execution(Long id, Workload workload) {
        ComponentTemplate http = new ComponentTemplate("http-service", "HTTP Service", ComponentType.HTTP_SERVICE,
                true, new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 0)));
        Scenario scenario = new Scenario(new Project(
                new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER), "Lab", null), "Run", null);
        scenario.replaceGraph(1, List.of(new ComponentDraft(ORDERS, "http-service", "orders-api", 0, 0,
                new ComponentConfiguration("STATELESS", Map.of("responseDelayMs", 50)))), List.of(), workload,
                Map.of("http-service", http));
        Execution execution = new Execution(scenario, Map.of(ComponentType.HTTP_SERVICE, 1));
        ReflectionTestUtils.setField(execution, "id", id);
        return execution;
    }

    private static String reference(String path) throws IOException {
        try (InputStream input = PlanCompilerTests.class.getClassLoader().getResourceAsStream(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
