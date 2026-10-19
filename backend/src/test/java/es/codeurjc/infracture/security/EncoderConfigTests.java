package es.codeurjc.infracture.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class EncoderConfigTests {

    private final PasswordEncoder encoder = new EncoderConfig().passwordEncoder();

    @Test
    void hashesPasswordsWithArgon2idWithoutTruncatingUnicode() {
        String password = "😀".repeat(63) + "x";
        String encoded = encoder.encode(password);

        assertThat(encoded).startsWith("$argon2id$");
        assertThat(encoder.matches(password, encoded)).isTrue();
        assertThat(encoder.matches("😀".repeat(63) + "y", encoded)).isFalse();
    }

}
