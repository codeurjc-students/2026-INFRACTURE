package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogRedactorTests {

    private final LogRedactor redactor = new LogRedactor("db-password-2026", "A secure initial admin 2026!");

    @Test
    void hidesTheBackendsOwnSecretsWhereverTheyAppear() {
        assertThat(redactor.clean("jdbc user=infracture pass db-password-2026 and A secure initial admin 2026!"))
                .isEqualTo("jdbc user=infracture pass [redacted] and [redacted]");
    }

    @Test
    void hidesTheValueAfterASecretsNameBearerTokensAndJwts() {
        assertThat(redactor.clean("password=hunter2 Token: \"abc def\" api_key=k-1"))
                .isEqualTo("password=[redacted] Token: [redacted] api_key=[redacted]");
        assertThat(redactor.clean("Authorization: Bearer abc.DEF-123=="))
                .isEqualTo("Authorization: Bearer [redacted]");
        assertThat(redactor.clean("cookie AuthToken=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhIn0.c2lnbmF0dXJl;"))
                .isEqualTo("cookie AuthToken=[redacted];");
    }

    @Test
    void keepsOrdinaryLinesAndCutsLongOnes() {
        assertThat(redactor.clean("Started DemoApplication in 1.2 seconds")).isEqualTo(
                "Started DemoApplication in 1.2 seconds");
        assertThat(redactor.clean("x".repeat(600))).hasSize(LogRedactor.MAX_LINE_LENGTH);
    }

    @Test
    void ignoresSecretsTooShortToTellFromOrdinaryWords() {
        LogRedactor withShortSecret = new LogRedactor("abc", "");

        assertThat(withShortSecret.clean("abcdef")).isEqualTo("abcdef");
    }
}
