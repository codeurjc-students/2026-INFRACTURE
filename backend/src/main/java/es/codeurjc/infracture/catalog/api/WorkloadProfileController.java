package es.codeurjc.infracture.catalog.api;

import es.codeurjc.infracture.catalog.domain.WorkloadContract;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The load profiles a scenario can use, with the same limits the validator applies (ADR 0016).
@RestController
@RequestMapping("/api/v1/workload-profiles")
public class WorkloadProfileController {

    private final ComponentTemplateMapper mapper;

    public WorkloadProfileController(ComponentTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<WorkloadProfileDTO> getWorkloadProfiles() {
        return WorkloadContract.all().stream().map(mapper::toDTO).toList();
    }
}
