package es.codeurjc.infracture.execution.application;

// Docker could not do what the plan asks. The message becomes the execution's failure reason.
public class RuntimeEngineException extends RuntimeException {

    public RuntimeEngineException(String message) {
        super(message);
    }

    public RuntimeEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
