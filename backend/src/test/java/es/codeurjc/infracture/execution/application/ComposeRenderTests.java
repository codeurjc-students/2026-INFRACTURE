package es.codeurjc.infracture.execution.application;

import static es.codeurjc.infracture.execution.application.PlanCompilerTests.LOAD;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.LOAD_IMAGE;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.execution;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.reference;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.json.JsonMapper;

// The engine is the only place that writes Compose. The reference files are the Compose plans the backend
// stored before the plan was typed, so the same snapshot still reaches Docker as exactly the same file.
class ComposeRenderTests {

    private final PlanCompiler compiler = new PlanCompiler(
            List.of(new HttpServiceDriver("infracture/demo-app:0.1.0")), "local", LOAD_IMAGE);

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
    void givesExperimentsNoPortsVolumesPrivilegesOrSecrets() {
        Map<String, Object> file = ComposeRuntimeEngine.render(compiler.compile(execution(12L, LOAD)));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> services = (Map<String, Map<String, Object>>) file.get("services");

        assertThat(services).hasSize(2).allSatisfy((name, service) -> assertThat(service)
                .doesNotContainKeys("ports", "volumes", "privileged", "secrets", "command", "network_mode"));
        assertThat(file).doesNotContainKeys("volumes", "secrets");
    }
}
