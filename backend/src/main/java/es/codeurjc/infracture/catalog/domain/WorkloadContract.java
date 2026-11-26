package es.codeurjc.infracture.catalog.domain;

import java.util.List;
import java.util.Map;

// A load profile: its integer parameters and the capabilities its target must offer.
// initialParameters are the values a new load starts with in the editor.
public record WorkloadContract(String profile, List<ParameterContract> parameters, List<Capability> requires,
        Map<String, Integer> initialParameters) {

    public static final ParameterContract SEED = new ParameterContract("seed", 0, Integer.MAX_VALUE);
}
