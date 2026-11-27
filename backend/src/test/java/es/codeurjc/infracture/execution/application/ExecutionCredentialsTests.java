package es.codeurjc.infracture.execution.application;

import static es.codeurjc.infracture.execution.application.PlanCompilerTests.DATABASE;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.databaseExecution;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.execution;
import static org.assertj.core.api.Assertions.assertThat;

import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExecutionCredentialsTests {

    private static final String VARIABLE = "INFRACTURE_SECRET_7c2e4a6b8d0f4b1c9e3a5f7b9d1c3e5a";

    private final PlanCompiler compiler = new PlanCompiler(
            List.of(new HttpServiceDriver("image"), new PostgresqlDriver("postgres")), "local", "k6");
    private final ExecutionCredentials credentials = new ExecutionCredentials();

    @Test
    void givesEachNodeOnePasswordThatEveryCommandOfTheRunRepeats() {
        ExecutionPlan plan = compiler.compile(databaseExecution(12L));

        assertThat(ExecutionCredentials.variable(DATABASE)).isEqualTo(VARIABLE);
        assertThat(credentials.environment(plan)).containsOnlyKeys(VARIABLE)
                .isEqualTo(credentials.environment(compiler.compile(databaseExecution(12L))));
        assertThat(credentials.environment(plan).get(VARIABLE)).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void changesThePasswordWithTheExecutionAndWithEachBackendStart() {
        String first = credentials.environment(compiler.compile(databaseExecution(12L))).get(VARIABLE);

        assertThat(credentials.environment(compiler.compile(databaseExecution(13L))).get(VARIABLE))
                .isNotEqualTo(first);
        assertThat(new ExecutionCredentials().environment(compiler.compile(databaseExecution(12L))).get(VARIABLE))
                .isNotEqualTo(first);
    }

    @Test
    void neverWritesAPasswordIntoThePlanAndNamesNoneForANodeWithoutOne() {
        ExecutionPlan plan = compiler.compile(databaseExecution(12L));

        assertThat(JsonMapper.builder().build().writeValueAsString(plan))
                .doesNotContain(credentials.environment(plan).get(VARIABLE));
        assertThat(credentials.environment(compiler.compile(execution(12L)))).isEmpty();
    }
}
