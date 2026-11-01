package es.codeurjc.infracture.project.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConnectionDTO(@NotNull UUID sourceKey, @NotNull String port, @NotNull UUID targetKey) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected connection field: " + name);
    }
}
