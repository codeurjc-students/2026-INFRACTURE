package es.codeurjc.infracture.identity.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record ChangePasswordDTO(
        String currentPassword,
        String newPassword) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected account field: " + name);
    }

    @Override
    public String toString() {
        return "ChangePasswordDTO[]";
    }
}
