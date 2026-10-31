package es.codeurjc.infracture.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "initial_configuration", nullable = false)
    private ComponentConfiguration initialConfiguration;

    protected ComponentTemplate() {
    }

    public ComponentTemplate(String key, String name, ComponentType type, boolean enabled,
            ComponentConfiguration initialConfiguration) {
        this.key = key;
        this.name = name;
        this.type = type;
        this.enabled = enabled;
        this.initialConfiguration = Objects.requireNonNull(initialConfiguration, "initialConfiguration");
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

    public ComponentConfiguration getInitialConfiguration() {
        return initialConfiguration;
    }

}
