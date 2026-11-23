package es.codeurjc.infracture.security.jwt;

public record LoginRequest(String email, String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
