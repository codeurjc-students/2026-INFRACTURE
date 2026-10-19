package es.codeurjc.infracture.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
