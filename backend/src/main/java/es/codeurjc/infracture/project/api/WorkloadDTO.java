package es.codeurjc.infracture.project.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

// The scenario load
public record WorkloadDTO(UUID targetKey, String profile, @NotNull Map<String, @NotNull Integer> parameters,
        @NotNull Integer seed) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected workload field: " + name);
    }
}
