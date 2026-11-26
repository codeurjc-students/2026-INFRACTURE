package es.codeurjc.infracture.catalog.application;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.catalog.domain.ComponentTypeDefinition;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import es.codeurjc.infracture.catalog.domain.WorkloadContract;
import es.codeurjc.infracture.catalog.domain.WorkloadProfileDefinition;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Every contract the application knows: one per node type and one per load profile, each from its own bean.
@Component
public class ContractCatalog {

    private final Map<ComponentType, TemplateContract> contracts = new EnumMap<>(ComponentType.class);
    private final Map<String, WorkloadContract> workloads = new LinkedHashMap<>();

    public ContractCatalog(List<ComponentTypeDefinition> definitions, List<WorkloadProfileDefinition> profiles) {
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
        for (WorkloadProfileDefinition profile : profiles) {
            WorkloadContract contract = profile.contract();
            if (workloads.put(contract.profile(), contract) != null) {
                throw new IllegalStateException(contract.profile() + " has more than one definition");
            }
        }
    }

    public Optional<TemplateContract> find(ComponentType type) {
        return Optional.ofNullable(contracts.get(type));
    }

    public List<WorkloadContract> workloads() {
        return List.copyOf(workloads.values());
    }

    public Optional<WorkloadContract> workload(String profile) {
        return Optional.ofNullable(workloads.get(profile));
    }
}
