package es.codeurjc.infracture.identity.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record UpdateProfileDTO(
        String displayName,
        String username) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected account field: " + name);
    }
}
