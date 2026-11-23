package es.codeurjc.infracture.security;

import es.codeurjc.infracture.identity.domain.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// The signed-in account. Tokens identify it by id and version; getUsername stays the current email, so
// Principal.getName() keeps working for the services that look up what an account owns.
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final Long id;
    private final int tokenVersion;
    private final String email;
    private final List<GrantedAuthority> authorities;
    private final boolean enabled;
    private String password;

    private AuthenticatedUser(User user) {
        this.id = user.getId();
        this.tokenVersion = user.getTokenVersion();
        this.email = user.getEmail();
        this.password = user.getEncodedPassword();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        this.enabled = user.isActive();
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user);
    }

    public Long getId() {
        return id;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
