package es.codeurjc.infracture.security.jwt;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import es.codeurjc.infracture.security.RepositoryUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtRequestFilter extends OncePerRequestFilter {

    // When the access token of an authenticated request expires. A long-lived response, such as an event
    // stream, ends at that time so a logged-out or expired session stops receiving data.
    public static final String TOKEN_EXPIRES_AT = JwtRequestFilter.class.getName() + ".tokenExpiresAt";

    private static final Logger log = LoggerFactory.getLogger(JwtRequestFilter.class);

    private final RepositoryUserDetailsService userDetailsService;

    private final JwtTokenProvider jwtTokenProvider;

    public JwtRequestFilter(RepositoryUserDetailsService userDetailsService, JwtTokenProvider jwtTokenProvider) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            var claims = jwtTokenProvider.validateToken(request);
            if (!TokenType.ACCESS.name().equals(claims.get("type", String.class))) {
                throw new BadCredentialsException("Invalid access token type");
            }
            var userDetails = userDetailsService.loadSession(claims);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute(TOKEN_EXPIRES_AT, claims.getExpiration().toInstant());
        } catch (Exception ex) {
            log.debug(ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

}
