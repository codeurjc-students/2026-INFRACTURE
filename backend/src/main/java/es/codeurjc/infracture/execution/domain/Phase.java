package es.codeurjc.infracture.execution.domain;

// What the engine is doing with an execution. Only RELEASED frees the reservation.
public enum Phase {
    RESERVED,
    STARTING,
    RUNNING,
    STOPPING,
    RELEASED
}
