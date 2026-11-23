package es.codeurjc.infracture.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegisterUserDTOTests {

    @Test
    void leavesThePasswordOutOfItsTextRepresentation() {
        RegisterUserDTO request = new RegisterUserDTO("alice", "Alice", "alice@example.com",
                "A secure passphrase 2026!");

        assertThat(request.toString()).contains("alice@example.com").doesNotContain("A secure passphrase 2026!");
    }
}
