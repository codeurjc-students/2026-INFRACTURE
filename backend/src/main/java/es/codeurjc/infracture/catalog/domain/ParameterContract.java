package es.codeurjc.infracture.catalog.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ParameterContract(String name, int min, int max) {

    public static List<ContractViolation> check(List<ParameterContract> allowed, Map<String, Integer> parameters,
            String profile) {
        List<ContractViolation> violations = new ArrayList<>();
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
