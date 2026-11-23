package es.codeurjc.infracture.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginRequestTests {

    @Test
    void leavesThePasswordOutOfItsTextRepresentation() {
        LoginRequest request = new LoginRequest("alice@example.com", "A secure passphrase 2026!");

        assertThat(request.toString()).contains("alice@example.com").doesNotContain("A secure passphrase 2026!");
    }
}
