package es.codeurjc.infracture.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "component_template")
public class ComponentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key")
    private String key;

    private String name;

    @Enumerated(EnumType.STRING)
    private ComponentType type;

    private boolean enabled;

    protected ComponentTemplate() {
    }

    public ComponentTemplate(String key, String name, ComponentType type, boolean enabled) {
        this.key = key;
        this.name = name;
        this.type = type;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public ComponentType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

}
