package es.codeurjc.infracture.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.function.Consumer;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

class UserServiceTests {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final String PASSWORD = "  A secure passphrase 2026!  ";

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(repository, encoder, VALIDATORS.getValidator(), new PasswordPolicy());

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATORS.close();
    }

    @Test
    void findsAccountByNormalizedEmailRatherThanUsername() {
        User account = validUser();
        when(repository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(account));

        assertThat(service.getUserByEmail(" ALICE@EXAMPLE.COM ")).isSameAs(account);
        verify(repository).findByEmailIgnoreCase("alice@example.com");
    }

    @Test
    void createsInitialAdministratorWithEncodedPassword() {
        User input = validUser();
        when(encoder.encode(PASSWORD)).thenReturn("encoded-admin-hash");

        service.initializeAdmin(input);

        assertThat(input.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(input.getEncodedPassword()).isEqualTo("encoded-admin-hash");
        verify(repository).save(input);
    }

    @Test
    void leavesExistingAdministratorUntouchedEvenWithoutConfiguration() {
        when(repository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        service.initializeAdmin(null);

        verify(repository, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void normalizesRegistrationAndAssignsServerControlledFields() {
        User input = new User(" ALICE ", PASSWORD, " ALICE@EXAMPLE.COM ", "  A\u0301lice O’Neill-Sáez  ", UserRole.ADMIN);
        input.setActive(false);
        input.setAvatarKey("untrusted-avatar");
        when(encoder.encode(PASSWORD)).thenReturn("encoded-hash");
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = service.registerUser(input);

        assertThat(saved).isSameAs(input);
        assertThat(saved.getId()).isNull();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("ALICE@EXAMPLE.COM");
        assertThat(saved.getDisplayName()).isEqualTo("Álice O’Neill-Sáez");
        assertThat(saved.getEncodedPassword()).isEqualTo("encoded-hash");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getAvatarKey()).isNull();
        verify(repository).save(input);
        verify(repository).existsByUsernameIgnoreCase("alice");
        verify(repository).existsByEmailIgnoreCase("alice@example.com");
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidAccountFieldsBeforeHashingOrSaving(Consumer<User> change, String field) {
        User input = validUser();
        change.accept(input);

        assertThatThrownBy(() -> service.registerUser(input))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining(field);
        verifyNoInteractions(repository, encoder);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of((Consumer<User>) user -> user.setUsername("alice-smith"), "username"),
                Arguments.of((Consumer<User>) user -> user.setUsername("a".repeat(21)), "username"),
                Arguments.of((Consumer<User>) user -> user.setDisplayName("   "), "displayName"),
                Arguments.of((Consumer<User>) user -> user.setDisplayName("a".repeat(31)), "displayName"),
                Arguments.of((Consumer<User>) user -> user.setDisplayName("Alice 😀"), "displayName"),
                Arguments.of((Consumer<User>) user -> user.setEmail("not-an-email"), "email"));
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true"})
    void rejectsExistingUsernameOrEmailBeforeHashing(boolean usernameExists, boolean emailExists) {
        when(repository.existsByUsernameIgnoreCase("alice")).thenReturn(usernameExists);
        when(repository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(emailExists);

        assertThatThrownBy(() -> service.registerUser(validUser())).isInstanceOf(UserAlreadyExistsException.class);

        verifyNoInteractions(encoder);
        verify(repository, never()).save(any());
    }

    @Test
    void preservesIntegrityFailuresUnrelatedToDuplicateIdentifiers() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("Other database constraint");
        when(repository.save(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.registerUser(validUser())).isSameAs(failure);
    }

    @Test
    void rejectsAnExistingEntityInsteadOfUpdatingItThroughRegistration() {
        User input = validUser();
        input.setId(1L);

        assertThatThrownBy(() -> service.registerUser(input)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, encoder);
    }

    private User validUser() {
        return new User("alice", PASSWORD, "alice@example.com", "Alice", UserRole.USER);
    }
}
