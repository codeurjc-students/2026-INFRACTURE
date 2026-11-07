package es.codeurjc.infracture.execution.application;

// What Docker says about one node of a running experiment.
public enum ComponentStatus {
    STARTING,
    HEALTHY,
    UNHEALTHY,
    EXITED;

    // State and Health come from "docker compose ps --format json".
    static ComponentStatus of(String state, String health) {
        if ("created".equals(state)) {
            return STARTING;
        }
        if (!"running".equals(state)) {
            return EXITED;
        }
        if ("unhealthy".equals(health)) {
            return UNHEALTHY;
        }
        return "starting".equals(health) ? STARTING : HEALTHY;
    }
}
