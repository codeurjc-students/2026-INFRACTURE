package es.codeurjc.infracture.demo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Every request of the demonstration API waits responseDelayMs before it is handled; /health never waits.
@Configuration
public class ResponseDelay implements WebMvcConfigurer {

    private final int responseDelayMs;

    public ResponseDelay(@Value("${RESPONSE_DELAY_MS:0}") int responseDelayMs) {
        this.responseDelayMs = responseDelayMs;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws InterruptedException {
                Thread.sleep(Math.max(responseDelayMs, 0));
                return true;
            }
        }).addPathPatterns("/", "/items", "/items/**");
    }
}
