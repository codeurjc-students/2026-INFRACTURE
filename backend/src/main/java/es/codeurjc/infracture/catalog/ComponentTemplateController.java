package es.codeurjc.infracture.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping
    public List<ComponentTemplateDTO> getComponentTemplates() {
        return mapper.toDTOs(service.getEnabledTemplates());
    }
}
