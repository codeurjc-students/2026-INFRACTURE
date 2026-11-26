package es.codeurjc.infracture.catalog.domain;

import java.util.List;

// consumes marks a port that takes messages out of the node it connects to, like the Worker's queue.
public record PortContract(String name, String label, List<Capability> accepts, int min, int max, boolean consumes) {

    public PortContract(String name, String label, List<Capability> accepts, int min, int max) {
        this(name, label, accepts, min, max, false);
    }
}
