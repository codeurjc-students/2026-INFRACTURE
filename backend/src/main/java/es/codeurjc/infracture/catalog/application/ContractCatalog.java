package es.codeurjc.infracture.catalog.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.domain.ComponentTypeDefinition;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ContractCatalog {

    private final Map<ComponentType, TemplateContract> contracts = new EnumMap<>(ComponentType.class);

    public ContractCatalog(List<ComponentTypeDefinition> definitions) {
        for (ComponentTypeDefinition definition : definitions) {
            if (contracts.put(definition.type(), definition.contract()) != null) {
                throw new IllegalStateException(definition.type() + " has more than one definition");
            }
        }
        for (ComponentType type : ComponentType.values()) {
            if (!contracts.containsKey(type)) {
                throw new IllegalStateException(type + " has no definition");
            }
        }
    }

    public Optional<TemplateContract> find(ComponentType type) {
        return Optional.ofNullable(contracts.get(type));
    }
}
