package es.codeurjc.infracture.project.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record SaveProjectDTO(
        String name,
        String description) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected project field: " + name);
    }
}
