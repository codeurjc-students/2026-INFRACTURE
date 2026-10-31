package es.codeurjc.infracture.project.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record SaveComponentDTO(
        @NotNull UUID key,
        String templateKey,
        String name,
        @NotNull @Valid PositionDTO position,
        String profile,
        @NotNull Map<String, @NotNull Integer> parameters) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected component field: " + name);
    }
}
