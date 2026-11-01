package es.codeurjc.infracture.catalog.application;

import es.codeurjc.infracture.catalog.domain.Capability;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.domain.ComponentTypeDefinition;
import es.codeurjc.infracture.catalog.domain.ProfileContract;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RedisDefinition implements ComponentTypeDefinition {

    private static final TemplateContract CONTRACT = new TemplateContract(ComponentType.REDIS, 1, List.of(
            new ProfileContract("DEFAULT", List.of(), List.of(), List.of(Capability.KEY_VALUE))));

    @Override
    public ComponentType type() {
        return ComponentType.REDIS;
    }

    @Override
    public TemplateContract contract() {
        return CONTRACT;
    }
}
