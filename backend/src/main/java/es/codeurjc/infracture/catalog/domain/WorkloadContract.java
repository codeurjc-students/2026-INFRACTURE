package es.codeurjc.infracture.catalog.domain;

import java.util.List;
import java.util.Optional;

// A load profile: its integer parameters and the capabilities its target must offer.
public record WorkloadContract(String profile, List<ParameterContract> parameters, List<Capability> requires) {

    public static final ParameterContract SEED = new ParameterContract("seed", 0, Integer.MAX_VALUE);

    // shortcut: CONSTANT_HTTP is the only profile until the other three get their
    // k6 scripts.
    private static final List<WorkloadContract> PROFILES = List.of(new WorkloadContract("CONSTANT_HTTP", List.of(
            new ParameterContract("requestsPerSecond", 1, 50),
            new ParameterContract("virtualUsers", 1, 20),
            new ParameterContract("durationSeconds", 10, 600)), List.of(Capability.HTTP)));

    public static Optional<WorkloadContract> find(String profile) {
        return PROFILES.stream().filter(candidate -> candidate.profile().equals(profile)).findFirst();
    }
}
