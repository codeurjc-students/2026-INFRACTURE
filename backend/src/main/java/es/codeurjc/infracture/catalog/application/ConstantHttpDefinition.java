package es.codeurjc.infracture.catalog.application;

import es.codeurjc.infracture.catalog.domain.Capability;
import es.codeurjc.infracture.catalog.domain.ParameterContract;
import es.codeurjc.infracture.catalog.domain.WorkloadContract;
import es.codeurjc.infracture.catalog.domain.WorkloadProfileDefinition;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

// A steady rate of HTTP requests, with the limits of ADR 0016.
@Component
public class ConstantHttpDefinition implements WorkloadProfileDefinition {

    private static final WorkloadContract CONTRACT = new WorkloadContract("CONSTANT_HTTP", List.of(
            new ParameterContract("requestsPerSecond", 1, 50),
            new ParameterContract("virtualUsers", 1, 20),
            new ParameterContract("durationSeconds", 10, 600)), List.of(Capability.HTTP),
            Map.of("requestsPerSecond", 5, "virtualUsers", 2, "durationSeconds", 60));

    @Override
    public WorkloadContract contract() {
        return CONTRACT;
    }
}
