package es.codeurjc.infracture.security.jwt;

import java.text.Normalizer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import es.codeurjc.infracture.security.AuthenticatedUser;
import es.codeurjc.infracture.security.RepositoryUserDetailsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class UserLoginService {

    private final AuthenticationManager authenticationManager;
    private final RepositoryUserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;

    public UserLoginService(AuthenticationManager authenticationManager,
            RepositoryUserDetailsService userDetailsService,
            JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public ResponseEntity<AuthResponse> login(HttpServletResponse response, LoginRequest loginRequest) {

        String password = loginRequest.password();
        if (password != null) {
            password = Normalizer.normalize(password, Normalizer.Form.NFC);
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), password));
        } catch (AuthenticationException exception) {
            throw new BadCredentialsException("Invalid email or password", exception);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // The account as it was when the password was checked, so a change made meanwhile also ends this session.
        issueSession(response, (AuthenticatedUser) authentication.getPrincipal());

        HttpHeaders responseHeaders = new HttpHeaders();
        AuthResponse loginResponse = new AuthResponse(AuthResponse.Status.SUCCESS,
                "Auth successful. Tokens are created in cookie.");
        return ResponseEntity.ok().headers(responseHeaders).body(loginResponse);
    }

    // Sets new access and refresh cookies, for a login or for the session that changed its credentials.
    public void issueSession(HttpServletResponse response, AuthenticatedUser user) {
        response.addCookie(buildTokenCookie(TokenType.ACCESS, jwtTokenProvider.generateAccessToken(user)));
        response.addCookie(buildTokenCookie(TokenType.REFRESH, jwtTokenProvider.generateRefreshToken(user)));
    }

    public ResponseEntity<AuthResponse> refresh(HttpServletResponse response, String refreshToken) {
        try {
            Claims claims = jwtTokenProvider.validateToken(refreshToken);
            if (!TokenType.REFRESH.name().equals(claims.get("type", String.class))) {
                throw new BadCredentialsException("Invalid refresh token type");
            }
            AuthenticatedUser user = userDetailsService.loadSession(claims);

            var newAccessToken = jwtTokenProvider.generateAccessToken(user);
            response.addCookie(buildTokenCookie(TokenType.ACCESS, newAccessToken));

            AuthResponse loginResponse = new AuthResponse(AuthResponse.Status.SUCCESS,
                    "Auth successful. Tokens are created in cookie.");
            return ResponseEntity.ok().body(loginResponse);

        } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
            throw new InsufficientAuthenticationException("Invalid or expired refresh token", exception);
        }
    }

    public String logout(HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        response.addCookie(removeTokenCookie(TokenType.ACCESS));
        response.addCookie(removeTokenCookie(TokenType.REFRESH));

        return "logout successfully";
    }

    private Cookie buildTokenCookie(TokenType type, String token) {
        Cookie cookie = new Cookie(type.cookieName, token);
        cookie.setMaxAge((int) type.duration.getSeconds());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        return cookie;
    }

    private Cookie removeTokenCookie(TokenType type) {
        Cookie cookie = new Cookie(type.cookieName, "");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        return cookie;
    }
}
