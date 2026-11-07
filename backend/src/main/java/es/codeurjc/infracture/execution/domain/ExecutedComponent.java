package es.codeurjc.infracture.execution.domain;

import es.codeurjc.infracture.catalog.domain.ComponentType;
import es.codeurjc.infracture.project.domain.Component;
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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// A frozen copy of a node as it was when the execution was reserved.
@Entity
@Immutable
@Table(name = "executed_components")
public class ExecutedComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false, updatable = false)
    private Execution execution;

    @Column(name = "component_key", nullable = false, updatable = false)
    private UUID key;

    @Column(nullable = false, updatable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ComponentType type;

    @Column(name = "contract_version", nullable = false, updatable = false)
    private int contractVersion;

    @Column(nullable = false, updatable = false)
    private String profile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Integer> parameters;

    protected ExecutedComponent() {
    }

    ExecutedComponent(Execution execution, Component component, int contractVersion) {
        this.execution = execution;
        this.key = component.getKey();
        this.name = component.getName();
        this.type = component.getType();
        this.contractVersion = contractVersion;
        this.profile = component.getConfiguration().profile();
        this.parameters = Map.copyOf(component.getConfiguration().parameters());
    }

    public Long getId() {
        return id;
    }

    public UUID getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public ComponentType getType() {
        return type;
    }

    public int getContractVersion() {
        return contractVersion;
    }

    public String getProfile() {
        return profile;
    }

    public Map<String, Integer> getParameters() {
        return parameters;
    }
}
