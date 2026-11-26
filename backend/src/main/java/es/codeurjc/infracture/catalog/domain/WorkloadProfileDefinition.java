package es.codeurjc.infracture.catalog.domain;

// One load profile, registered as a bean like each ComponentTypeDefinition.
public interface WorkloadProfileDefinition {

    WorkloadContract contract();
}
