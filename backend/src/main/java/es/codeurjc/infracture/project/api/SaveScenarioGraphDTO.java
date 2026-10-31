package es.codeurjc.infracture.project.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SaveScenarioGraphDTO(
        @NotNull Integer revision,
        @NotNull List<@NotNull @Valid SaveComponentDTO> components) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected graph field: " + name);
    }
}
