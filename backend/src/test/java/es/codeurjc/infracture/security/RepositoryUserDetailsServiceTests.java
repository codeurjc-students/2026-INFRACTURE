package es.codeurjc.infracture.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class RepositoryUserDetailsServiceTests {

    private final UserRepository repository = mock(UserRepository.class);
    private final RepositoryUserDetailsService service = new RepositoryUserDetailsService(repository);

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void loadsEmailStoredPasswordAndSingleRole(UserRole role) {
        User user = new User("alice", "stored-password-hash", "Alice@Example.com", "Alice", role);
        when(repository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));

        var details = service.loadUserByUsername(" ALICE@EXAMPLE.COM ");

        assertThat(details.getUsername()).isEqualTo(user.getEmail());
        assertThat(details.getPassword()).isEqualTo(user.getEncodedPassword());
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_" + role.name());
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void loadsTheSessionByIdEvenAfterTheEmailChanged() {
        User user = storedAccount();
        user.setEmail("new@example.com");

        AuthenticatedUser session = service.loadSession(claims("7", 0));

        assertThat(session.getId()).isEqualTo(7L);
        assertThat(session.getTokenVersion()).isZero();
        assertThat(session.getUsername()).isEqualTo("new@example.com");
    }

    @Test
    void rejectsATokenOfAnotherVersion() {
        storedAccount().revokeSessions();

        assertThatThrownBy(() -> service.loadSession(claims("7", 0)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsASessionOfAnInactiveAccount() {
        storedAccount().setActive(false);

        assertThatThrownBy(() -> service.loadSession(claims("7", 0)))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void rejectsASessionOfAMissingAccount() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadSession(claims("9", 0)))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // The filter and the refresh treat these failures as "not authenticated".
    @ParameterizedTest
    @MethodSource("malformedClaims")
    void rejectsMalformedSubjectOrVersion(Claims claims) {
        storedAccount();

        assertThatThrownBy(() -> service.loadSession(claims))
                .isInstanceOfAny(IllegalArgumentException.class, BadCredentialsException.class,
                        io.jsonwebtoken.JwtException.class);
    }

    static Stream<Claims> malformedClaims() {
        return Stream.of(
                Jwts.claims().add(Map.of("ver", 0)).build(),
                Jwts.claims().subject("alice@example.com").add("ver", 0).build(),
                Jwts.claims().subject("7").build(),
                Jwts.claims().subject("7").add("ver", "0").build());
    }

    private User storedAccount() {
        User user = new User("alice", "stored-password-hash", "alice@example.com", "Alice", UserRole.USER);
        user.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        return user;
    }

    private static Claims claims(String subject, int version) {
        return Jwts.claims().subject(subject).add("ver", version).build();
    }

    @Test
    void rejectsUnknownEmail() {
        when(repository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void authenticatesUsingStoredPasswordHash() {
        var encoder = new EncoderConfig().passwordEncoder();
        User user = new User("alice", encoder.encode("test-password"), "alice@example.com", "Alice", UserRole.USER);
        when(repository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        var provider = new DaoAuthenticationProvider(service);
        provider.setPasswordEncoder(encoder);

        var authentication = provider.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "test-password"));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo(user.getEmail());
    }

    @Test
    void preventsAuthenticationForInactiveAccount() {
        var encoder = new EncoderConfig().passwordEncoder();
        User user = new User("alice", encoder.encode("test-password"), "alice@example.com", "Alice", UserRole.USER);
        user.setActive(false);
        when(repository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        var provider = new DaoAuthenticationProvider(service);
        provider.setPasswordEncoder(encoder);

        assertThat(service.loadUserByUsername(user.getEmail()).isEnabled()).isFalse();
        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "test-password")))
                .isInstanceOf(DisabledException.class);
    }
}
