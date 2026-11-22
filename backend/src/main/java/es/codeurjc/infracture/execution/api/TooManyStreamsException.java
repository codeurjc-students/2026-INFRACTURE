package es.codeurjc.infracture.execution.api;

// The account already has as many event streams open as it may.
class TooManyStreamsException extends RuntimeException {

    TooManyStreamsException(int limit) {
        super("You already follow this experiment in %d tabs. Close one to open another.".formatted(limit));
    }
}
