package es.codeurjc.infracture.execution.application;

// The engine stopped waiting for a start because the execution must stop. It is not a failure: the cause
// that asked for the stop is already recorded, and the cleanup removes whatever the start left.
public class StartCancelledException extends RuntimeEngineException {

    public StartCancelledException(String message) {
        super(message);
    }
}
