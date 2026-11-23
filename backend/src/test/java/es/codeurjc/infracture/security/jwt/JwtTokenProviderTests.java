package es.codeurjc.infracture.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.security.AuthenticatedUser;
import es.codeurjc.infracture.security.MutableClock;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtTokenProviderTests {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
    private final JwtTokenProvider provider = new JwtTokenProvider(clock);
    private final AuthenticatedUser user = account();

    private static AuthenticatedUser account() {
        User account = new User("alice", "stored-password-hash", "alice@example.com", "Alice", UserRole.USER);
        account.setId(7L);
        account.revokeSessions();
        return AuthenticatedUser.from(account);
    }

    @Test
    void identifiesTheAccountByIdAndVersionWithoutItsEmail() {
        for (String token : new String[] { provider.generateAccessToken(user),
                provider.generateRefreshToken(user) }) {
            var claims = provider.validateToken(token);

            assertThat(claims.getSubject()).isEqualTo("7");
            assertThat(claims.get("ver", Integer.class)).isEqualTo(1);
            assertThat(claims.toString()).doesNotContain("alice@example.com");
        }
    }

    @Test
    void acceptsAnAccessTokenUntilItsLifetimeEnds() {
        String token = provider.generateAccessToken(user);

        clock.advance(TokenType.ACCESS.duration.minusSeconds(1));
        assertThat(provider.validateToken(token).getSubject()).isEqualTo("7");

        clock.advance(Duration.ofSeconds(2));
        assertThatThrownBy(() -> provider.validateToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void keepsTheRefreshTokenValidAfterTheAccessTokenExpires() {
        String refreshToken = provider.generateRefreshToken(user);

        clock.advance(TokenType.ACCESS.duration.plusSeconds(1));

        assertThat(provider.validateToken(refreshToken).get("type", String.class)).isEqualTo("REFRESH");
    }

    @Test
    void readsTheAccessTokenFromItsCookieAndIgnoresTheAuthorizationHeader() {
        String token = provider.generateAccessToken(user);
        MockHttpServletRequest withCookie = new MockHttpServletRequest();
        withCookie.setCookies(new Cookie(TokenType.ACCESS.cookieName, token));
        MockHttpServletRequest withHeader = new MockHttpServletRequest();
        withHeader.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        assertThat(provider.validateToken(withCookie).getSubject()).isEqualTo("7");
        assertThatThrownBy(() -> provider.validateToken(withHeader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(token);
    }
}
