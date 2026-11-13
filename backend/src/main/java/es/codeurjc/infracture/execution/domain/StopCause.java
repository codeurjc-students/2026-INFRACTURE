package es.codeurjc.infracture.execution.domain;

public enum StopCause {
    STOP_REQUESTED(1, Outcome.COMPLETED),
    // A normal end like Stop: between the two, the first one recorded stays.
    TIME_LIMIT(1, Outcome.COMPLETED),
    FAILURE(2, Outcome.FAILED),
    INTERRUPTION(3, Outcome.INTERRUPTED);

    private final int severity;
    private final Outcome outcome;

    StopCause(int severity, Outcome outcome) {
        this.severity = severity;
        this.outcome = outcome;
    }

    public Outcome outcome() {
        return outcome;
    }

    boolean isWorseThan(StopCause other) {
        return other == null || severity > other.severity;
    }
}
