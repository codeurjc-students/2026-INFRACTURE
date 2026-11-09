package es.codeurjc.infracture.catalog.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record TemplateContract(ComponentType type, int version, List<ProfileContract> profiles) {

    public Optional<ProfileContract> profile(String name) {
        return profiles.stream().filter(candidate -> candidate.name().equals(name)).findFirst();
    }

    public List<ContractViolation> check(ComponentConfiguration configuration) {
        List<ContractViolation> violations = new ArrayList<>();
        String profile = configuration.profile();
        if (profile == null) {
            violations.add(new ContractViolation("MISSING_PROFILE", "profile", "Profile is required"));
            return violations;
        }
        Optional<ProfileContract> match = profile(profile);
        if (match.isEmpty()) {
            violations.add(new ContractViolation("UNKNOWN_PROFILE", "profile",
                    "%s has no profile %s".formatted(type, profile)));
            return violations;
        }
        violations.addAll(ParameterContract.check(match.get().parameters(), configuration.parameters(), profile));
        return violations;
    }
}
