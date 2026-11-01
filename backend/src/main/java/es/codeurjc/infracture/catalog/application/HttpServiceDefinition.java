package es.codeurjc.infracture.catalog.application;

import es.codeurjc.infracture.catalog.domain.Capability;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.domain.ComponentTypeDefinition;
import es.codeurjc.infracture.catalog.domain.ParameterContract;
import es.codeurjc.infracture.catalog.domain.PortContract;
import es.codeurjc.infracture.catalog.domain.ProfileContract;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HttpServiceDefinition implements ComponentTypeDefinition {

    private static final List<ParameterContract> PARAMETERS = List.of(new ParameterContract("responseDelayMs", 0, 5000));
    private static final PortContract DATABASE = new PortContract("database", "Database",
            List.of(Capability.SQL_DATABASE), 1, 1);
    private static final List<Capability> RESOURCES = List.of(Capability.HTTP, Capability.RESOURCE_READ,
            Capability.RESOURCE_WRITE);

    private static final TemplateContract CONTRACT = new TemplateContract(ComponentType.HTTP_SERVICE, 1, List.of(
            new ProfileContract("STATELESS", PARAMETERS, List.of(), List.of(Capability.HTTP)),
            new ProfileContract("DATABASE_CRUD", PARAMETERS, List.of(DATABASE), RESOURCES),
            new ProfileContract("CACHE_ASIDE", PARAMETERS, List.of(
                    new PortContract("cache", "Cache", List.of(Capability.KEY_VALUE), 1, 1), DATABASE), RESOURCES),
            new ProfileContract("QUEUE_PRODUCER", PARAMETERS, List.of(
                    new PortContract("queue", "Publishes to", List.of(Capability.MESSAGE_BROKER), 1, 1)),
                    List.of(Capability.HTTP, Capability.TASK_CREATE)),
            new ProfileContract("DOWNSTREAM_HTTP", PARAMETERS, List.of(
                    new PortContract("downstream", "Calls", List.of(Capability.HTTP), 1, 1)),
                    List.of(Capability.HTTP))));

    @Override
    public ComponentType type() {
        return ComponentType.HTTP_SERVICE;
    }

    @Override
    public TemplateContract contract() {
        return CONTRACT;
    }
}
