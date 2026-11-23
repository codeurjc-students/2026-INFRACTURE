package es.codeurjc.infracture.security.jwt;

public record AuthResponse(Status status, String message, String error) {

    public enum Status {
        SUCCESS
    }

    public AuthResponse(Status status, String message) {
        this(status, message, null);
    }
}
