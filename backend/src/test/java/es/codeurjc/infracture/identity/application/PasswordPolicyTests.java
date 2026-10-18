package es.codeurjc.infracture.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PasswordPolicyTests {

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @MethodSource("acceptedPasswords")
    void acceptsPasswordsMeetingCompositionAndLengthRequirements(String password) {
        assertThatCode(() -> policy.validate(password, "alice", "alice@example.com", "Alice"))
                .doesNotThrowAnyException();
    }

    static Stream<String> acceptedPasswords() {
        return Stream.of("Un bosque 2026!", "Áa1!" + "😀".repeat(124), "Un bosque 2026! 🇪🇸1️⃣");
    }

    @ParameterizedTest
    @MethodSource("rejectedPasswords")
    void rejectsPasswordsViolatingPolicy(String password, String message) {
        assertThatThrownBy(() -> policy.validate(password, "alice", "alice@example.com", "Alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    static Stream<Arguments> rejectedPasswords() {
        return Stream.of(
                Arguments.of("a".repeat(14), "between 15 and 128"),
                Arguments.of("a".repeat(129), "between 15 and 128"),
                Arguments.of("    password    ", "not common"),
                Arguments.of("Un bosque 2026!\t", "control characters"),
                Arguments.of("Un bosque 2026!\uD800", "valid Unicode"),
                Arguments.of("un bosque 2026!", "uppercase letter"),
                Arguments.of("UN BOSQUE 2026!", "lowercase letter"),
                Arguments.of("Un bosque verde!", "number"),
                Arguments.of("Un bosque 2026x", "special character"));
    }
}
