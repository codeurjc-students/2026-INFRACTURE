package es.codeurjc.infracture.security.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class JwtTokenProvider {

    private final SecretKey jwtSecret = Jwts.SIG.HS256.key().build();
    private final Clock clock;
    private final JwtParser jwtParser;

    public JwtTokenProvider(Clock clock) {
        this.clock = clock;
        this.jwtParser = Jwts.parser().verifyWith(jwtSecret).clock(() -> Date.from(clock.instant())).build();
    }

    public String tokenStringFromHeaders(HttpServletRequest req){
        String bearerToken = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearerToken == null) {
            throw new IllegalArgumentException("Missing Authorization header");
        }
        if(!bearerToken.startsWith("Bearer ")){
            throw new IllegalArgumentException("Authorization header does not start with Bearer: " + bearerToken);
        }
        return bearerToken.substring(7);
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
                    throw new IllegalArgumentException("Cookie %s has null value".formatted(TokenType.ACCESS.cookieName));
                }

                return accessToken;
            }
        }
        throw new IllegalArgumentException("No access token cookie found in request");
    }

    public Claims validateToken(HttpServletRequest req, boolean fromCookie){
        var token = fromCookie?
                tokenStringFromCookies(req):
                tokenStringFromHeaders(req);
        return validateToken(token);
    }

    public Claims validateToken(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    public String generateAccessToken(UserDetails userDetails) {
        return buildToken(TokenType.ACCESS, userDetails).compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        var token = buildToken(TokenType.REFRESH, userDetails);
        return token.compact();
    }

    private JwtBuilder buildToken(TokenType tokenType, UserDetails userDetails) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .claim("roles", userDetails.getAuthorities())
                .claim("type", tokenType.name())
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(tokenType.duration)))
                .signWith(jwtSecret);
    }
}
