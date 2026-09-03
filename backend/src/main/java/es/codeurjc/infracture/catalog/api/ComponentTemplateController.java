package es.codeurjc.infracture.catalog.api;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.codeurjc.infracture.catalog.application.ComponentTemplateService;

@RestController
@RequestMapping("/api/v1/component-templates")
public class ComponentTemplateController {

    private final ComponentTemplateService service;
    private final ComponentTemplateMapper mapper;

    public ComponentTemplateController(
            ComponentTemplateService service,
            ComponentTemplateMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ComponentTemplateDTO> getComponentTemplates() {
        return mapper.toDTOs(service.getEnabledTemplates());
    }
}
