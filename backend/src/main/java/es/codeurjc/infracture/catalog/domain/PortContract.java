package es.codeurjc.infracture.catalog.domain;

import java.util.List;

public record PortContract(String name, String label, List<Capability> accepts, int min, int max) {
}
