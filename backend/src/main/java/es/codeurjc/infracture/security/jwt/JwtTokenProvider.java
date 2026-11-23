package es.codeurjc.infracture.security.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import es.codeurjc.infracture.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class JwtTokenProvider {

    public static final String VERSION_CLAIM = "ver";

    private final SecretKey jwtSecret = Jwts.SIG.HS256.key().build();
    private final Clock clock;
    private final JwtParser jwtParser;

    public JwtTokenProvider(Clock clock) {
        this.clock = clock;
        this.jwtParser = Jwts.parser().verifyWith(jwtSecret).clock(() -> Date.from(clock.instant())).build();
    }

    private String tokenStringFromCookies(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            throw new IllegalArgumentException("No cookies found in request");
        }

        for (Cookie cookie : cookies) {
            if (TokenType.ACCESS.cookieName.equals(cookie.getName())) {
                String accessToken = cookie.getValue();
                if (accessToken == null) {
                    throw new IllegalArgumentException(
                            "Cookie %s has null value".formatted(TokenType.ACCESS.cookieName));
                }

                return accessToken;
            }
        }
        throw new IllegalArgumentException("No access token cookie found in request");
    }

    public Claims validateToken(HttpServletRequest request) {
        return validateToken(tokenStringFromCookies(request));
    }

    public Claims validateToken(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    public String generateAccessToken(AuthenticatedUser user) {
        return buildToken(TokenType.ACCESS, user).compact();
    }

    public String generateRefreshToken(AuthenticatedUser user) {
        var token = buildToken(TokenType.REFRESH, user);
        return token.compact();
    }

    private JwtBuilder buildToken(TokenType tokenType, AuthenticatedUser user) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .claim("roles", user.getAuthorities())
                .claim("type", tokenType.name())
                .claim(VERSION_CLAIM, user.getTokenVersion())
                .subject(String.valueOf(user.getId()))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(tokenType.duration)))
                .signWith(jwtSecret);
    }
}
