package es.codeurjc.infracture.catalog.api;

import es.codeurjc.infracture.catalog.application.ComponentTemplateService;
import es.codeurjc.infracture.catalog.application.ContractCatalog;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/component-templates")
public class ComponentTemplateController {

    private final ComponentTemplateService service;
    private final ComponentTemplateMapper mapper;
    private final ContractCatalog contracts;

    public ComponentTemplateController(
        ComponentTemplateService service,
        ComponentTemplateMapper mapper,
        ContractCatalog contracts
    ) {
        this.service = service;
        this.mapper = mapper;
        this.contracts = contracts;
    }

    @GetMapping
    public Page<ComponentTemplateDTO> getComponentTemplates(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) ComponentType type,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);
        return service
            .getEnabledTemplates(pageable, q, type)
            .map(template -> mapper.toDTO(template, contracts));
    }
}
