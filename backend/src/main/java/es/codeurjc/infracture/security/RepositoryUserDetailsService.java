package es.codeurjc.infracture.security;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import es.codeurjc.infracture.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.util.Locale;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RepositoryUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public RepositoryUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AuthenticatedUser loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("User not found");
        }
        User user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return AuthenticatedUser.from(user);
    }

    // The account a valid token belongs to. Any failure means the request is not authenticated.
    public AuthenticatedUser loadSession(Claims claims) {
        long id = Long.parseLong(claims.getSubject());
        Integer version = claims.get(JwtTokenProvider.VERSION_CLAIM, Integer.class);
        if (version == null) {
            throw new BadCredentialsException("Token without a version");
        }
        User user = userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (user.getTokenVersion() != version) {
            throw new BadCredentialsException("Token of a revoked session");
        }
        AuthenticatedUser session = AuthenticatedUser.from(user);
        new AccountStatusUserDetailsChecker().check(session);
        return session;
    }
}
