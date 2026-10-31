package es.codeurjc.infracture.catalog.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.domain.ComponentTypeDefinition;
import es.codeurjc.infracture.catalog.domain.ProfileContract;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkerDefinition implements ComponentTypeDefinition {

    private static final TemplateContract CONTRACT = new TemplateContract(ComponentType.WORKER, 1, List.of(
            new ProfileContract("CONSUMER", List.of())));

    @Override
    public ComponentType type() {
        return ComponentType.WORKER;
    }

    @Override
    public TemplateContract contract() {
        return CONTRACT;
    }
}
