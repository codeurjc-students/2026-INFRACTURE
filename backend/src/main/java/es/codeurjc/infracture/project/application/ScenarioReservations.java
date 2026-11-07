package es.codeurjc.infracture.project.application;

// Tells whether an execution holds a scenario, which then stays read-only until its cleanup ends
// The execution module implements it, so the project module does not depend on executions.
public interface ScenarioReservations {

    boolean isReserved(Long scenarioId);
}
