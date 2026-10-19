package es.codeurjc.infracture.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.security.MutableClock;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtTokenProviderTests {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
    private final JwtTokenProvider provider = new JwtTokenProvider(clock);
    private final UserDetails user = User.withUsername("alice@example.com")
        .password("stored-password-hash").roles("USER").build();

    @Test
    void acceptsAnAccessTokenUntilItsLifetimeEnds() {
        String token = provider.generateAccessToken(user);

        clock.advance(TokenType.ACCESS.duration.minusSeconds(1));
        assertThat(provider.validateToken(token).getSubject()).isEqualTo("alice@example.com");

        clock.advance(Duration.ofSeconds(2));
        assertThatThrownBy(() -> provider.validateToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void keepsTheRefreshTokenValidAfterTheAccessTokenExpires() {
        String refreshToken = provider.generateRefreshToken(user);

        clock.advance(TokenType.ACCESS.duration.plusSeconds(1));

        assertThat(provider.validateToken(refreshToken).get("type", String.class)).isEqualTo("REFRESH");
    }
}
