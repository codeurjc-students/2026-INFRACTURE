package es.codeurjc.infracture.catalog.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.domain.ComponentTypeDefinition;
import es.codeurjc.infracture.catalog.domain.ParameterContract;
import es.codeurjc.infracture.catalog.domain.ProfileContract;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HttpServiceDefinition implements ComponentTypeDefinition {

    private static final TemplateContract CONTRACT = new TemplateContract(ComponentType.HTTP_SERVICE, 1, List.of(
            new ProfileContract("STATELESS", List.of(new ParameterContract("responseDelayMs", 0, 5000)))));

    @Override
    public ComponentType type() {
        return ComponentType.HTTP_SERVICE;
    }

    @Override
    public TemplateContract contract() {
        return CONTRACT;
    }
}
