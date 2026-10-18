package es.codeurjc.infracture.identity.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record RegisterUserDTO(
        String username,
        String displayName,
        String email,
        String password) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected registration field: " + name);
    }
}
