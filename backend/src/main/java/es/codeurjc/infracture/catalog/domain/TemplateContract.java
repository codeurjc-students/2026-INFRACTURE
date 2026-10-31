package es.codeurjc.infracture.catalog.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record TemplateContract(ComponentType type, int version, List<ProfileContract> profiles) {

    public List<ContractViolation> check(ComponentConfiguration configuration) {
        List<ContractViolation> violations = new ArrayList<>();
        String profile = configuration.profile();
        if (profile == null) {
            violations.add(new ContractViolation("MISSING_PROFILE", "profile", "Profile is required"));
            return violations;
        }
        Optional<ProfileContract> match = profiles.stream()
                .filter(candidate -> candidate.name().equals(profile))
                .findFirst();
        if (match.isEmpty()) {
            violations.add(new ContractViolation("UNKNOWN_PROFILE", "profile",
                    "%s has no profile %s".formatted(type, profile)));
            return violations;
        }
        List<ParameterContract> allowed = match.get().parameters();
        Map<String, Integer> parameters = configuration.parameters();
        for (ParameterContract parameter : allowed) {
            Integer value = parameters.get(parameter.name());
            if (value == null) {
                violations.add(new ContractViolation("MISSING_PARAMETER", parameter.name(),
                        parameter.name() + " is required"));
            } else if (value < parameter.min() || value > parameter.max()) {
                violations.add(new ContractViolation("PARAMETER_OUT_OF_RANGE", parameter.name(),
                        "%s must be between %d and %d".formatted(parameter.name(), parameter.min(), parameter.max())));
            }
        }
        parameters.keySet().stream()
                .filter(name -> allowed.stream().noneMatch(parameter -> parameter.name().equals(name)))
                .sorted()
                .forEach(name -> violations.add(new ContractViolation("UNEXPECTED_PARAMETER", name,
                        "%s does not accept %s".formatted(profile, name))));
        return violations;
    }
}
