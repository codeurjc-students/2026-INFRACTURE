package es.codeurjc.infracture.execution.application;

import static es.codeurjc.infracture.execution.application.PlanCompilerTests.DATABASE;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.databaseExecution;
import static es.codeurjc.infracture.execution.application.PlanCompilerTests.execution;
import static org.assertj.core.api.Assertions.assertThat;

import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogRedactorTests {

    private final ExecutionCredentials credentials = new ExecutionCredentials();
    private final LogRedactor redactor =
            new LogRedactor(credentials, "db-password-2026", "A secure initial admin 2026!");
    private final ExecutionPlan plan = new PlanCompiler(List.of(new HttpServiceDriver("image")), "local", "k6")
            .compile(execution(12L));

    @Test
    void hidesTheBackendsOwnSecretsWhereverTheyAppear() {
        assertThat(redactor.clean("jdbc user=infracture pass db-password-2026 and A secure initial admin 2026!",
                plan))
                .isEqualTo("jdbc user=infracture pass [redacted] and [redacted]");
    }

    @Test
    void hidesTheValueAfterASecretsNameBearerTokensAndJwts() {
        assertThat(redactor.clean("password=hunter2 Token: \"abc def\" api_key=k-1", plan))
                .isEqualTo("password=[redacted] Token: [redacted] api_key=[redacted]");
        assertThat(redactor.clean("Authorization: Bearer abc.DEF-123==", plan))
                .isEqualTo("Authorization: Bearer [redacted]");
        assertThat(redactor.clean("cookie AuthToken=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhIn0.c2lnbmF0dXJl;", plan))
                .isEqualTo("cookie AuthToken=[redacted];");
    }

    @Test
    void keepsOrdinaryLinesAndCutsLongOnes() {
        assertThat(redactor.clean("Started DemoApplication in 1.2 seconds", plan)).isEqualTo(
                "Started DemoApplication in 1.2 seconds");
        assertThat(redactor.clean("x".repeat(600), plan)).hasSize(LogRedactor.MAX_LINE_LENGTH);
    }

    @Test
    void ignoresSecretsTooShortToTellFromOrdinaryWords() {
        LogRedactor withShortSecret = new LogRedactor(credentials, "abc", "");

        assertThat(withShortSecret.clean("abcdef", plan)).isEqualTo("abcdef");
    }

    @Test
    void hidesTheTemporaryPasswordsOfTheExecutionsNodes() {
        ExecutionPlan database = new PlanCompiler(
                List.of(new HttpServiceDriver("image"), new PostgresqlDriver("postgres")), "local", "k6")
                .compile(databaseExecution(12L));
        String password = credentials.environment(database).get(ExecutionCredentials.variable(DATABASE));

        assertThat(redactor.clean("connecting as app:" + password + "@db", database))
                .isEqualTo("connecting as app:[redacted]@db");
    }
}
