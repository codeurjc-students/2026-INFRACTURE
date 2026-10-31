package es.codeurjc.infracture.project.domain;

import es.codeurjc.infracture.catalog.domain.ComponentConfiguration;
import es.codeurjc.infracture.catalog.domain.ComponentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "components")
public class Component {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false, updatable = false)
    private Scenario scenario;

    @Column(name = "component_key", nullable = false, updatable = false)
    private UUID key;

    @Column(name = "template_key", nullable = false, updatable = false)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ComponentType type;

    @Column(nullable = false)
    private String name;

    @Column(name = "position_x", nullable = false)
    private double positionX;

    @Column(name = "position_y", nullable = false)
    private double positionY;

    private String profile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Integer> parameters;

    protected Component() {
    }

    Component(Scenario scenario, UUID key, String templateKey, ComponentType type) {
        this.scenario = scenario;
        this.key = key;
        this.templateKey = templateKey;
        this.type = type;
    }

    void update(ComponentDraft draft) {
        this.name = draft.name();
        this.positionX = draft.positionX();
        this.positionY = draft.positionY();
        this.profile = draft.configuration().profile();
        this.parameters = new HashMap<>(draft.configuration().parameters());
    }

    public Long getId() {
        return id;
    }

    public UUID getKey() {
        return key;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public ComponentType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public double getPositionX() {
        return positionX;
    }

    public double getPositionY() {
        return positionY;
    }

    public ComponentConfiguration getConfiguration() {
        return new ComponentConfiguration(profile, Map.copyOf(parameters));
    }
}
