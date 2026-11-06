package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.execution.domain.ExecutedComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

//Compiler of the Componente to its Docker compose info.
@Component
class HttpServiceDriver implements ComponentDriver {

    private final String image;

    HttpServiceDriver(@Value("${infracture.execution.http-service-image:infracture/demo-app:0.1.0}") String image) {
        this.image = image;
    }

    @Override
    public ComponentType type() {
        return ComponentType.HTTP_SERVICE;
    }

    @Override
    public Map<String, Object> service(ExecutedComponent component) {
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("APP_ROLE", "http");
        environment.put("APP_MODE", component.getProfile());
        environment.put("COMPONENT_NAME", component.getName());
        environment.put("RESPONSE_DELAY_MS", String.valueOf(component.getParameters().get("responseDelayMs")));

        Map<String, Object> service = new LinkedHashMap<>();
        service.put("image", image);
        service.put("environment", environment);
        service.put("healthcheck", Map.of(
                "test", List.of("CMD", "wget", "-qO-", "http://127.0.0.1:8080/health"),
                "interval", "2s",
                "timeout", "2s",
                "retries", 15,
                "start_period", "60s"));
        return service;
    }
}
