package es.codeurjc.infracture.demo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Counts every request to the demonstration API and any that ends in 4xx, 5xx or an exception. A filter runs
// before handler selection, so rejections like 405 or 415 are counted too, while /health and /metrics are not.
@Component
public class ApiCounters extends OncePerRequestFilter {

    private final Counter requests;
    private final Counter errors;

    public ApiCounters(MeterRegistry registry) {
        this.requests = Counter.builder("infracture.requests").register(registry);
        this.errors = Counter.builder("infracture.request.errors").register(registry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"/".equals(path) && !"/items".equals(path) && !path.startsWith("/items/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error ex) {
            failed = true;
            throw ex;
        } finally {
            requests.increment();
            if (failed || response.getStatus() >= 400) {
                errors.increment();
            }
        }
    }
}
