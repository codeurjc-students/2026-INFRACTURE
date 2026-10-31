package es.codeurjc.infracture.project.domain;

import es.codeurjc.infracture.catalog.domain.ComponentTemplate;
import es.codeurjc.infracture.catalog.domain.TemplateContract;
import es.codeurjc.infracture.catalog.domain.TemplateContractRegistry;
import es.codeurjc.infracture.shared.ConflictException;
import es.codeurjc.infracture.shared.InvalidDataException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.CodePointLength;

@Entity
@Table(name = "scenarios")
public class Scenario {

    private static final int INITIAL_REVISION = 1;
    private static final int MAX_COMPONENTS = 50;
    private static final int MAX_NAME_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @NotBlank(message = "Scenario name is required")
    @CodePointLength(max = 30, message = "Scenario name must contain at most 30 characters")
    private String name;

    @CodePointLength(max = 280, message = "Scenario description must contain at most 280 characters")
    private String description;

    @Column(nullable = false)
    private int revision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Component> components = new ArrayList<>();

    protected Scenario() {
    }

    public Scenario(Project project, String name, String description) {
        this.project = project;
        this.name = name;
        this.description = description;
        this.revision = INITIAL_REVISION;
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        this.updatedAt = Instant.now();
    }

    public void changeDetails(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void replaceGraph(int expectedRevision, List<ComponentDraft> drafts,
            Map<String, ComponentTemplate> templates) {
        if (expectedRevision != revision) {
            throw new ConflictException("The scenario was saved after revision %d. Reload it to continue from revision %d."
                    .formatted(expectedRevision, revision));
        }
        if (drafts.size() > MAX_COMPONENTS) {
            throw new InvalidDataException("A scenario can have at most " + MAX_COMPONENTS + " components");
        }
        Set<UUID> keys = new HashSet<>();
        for (ComponentDraft draft : drafts) {
            if (draft.key() != null && !keys.add(draft.key())) {
                throw new InvalidDataException("Component " + draft.key() + " appears more than once");
            }
        }
        Map<UUID, Component> saved = components.stream()
                .collect(Collectors.toMap(Component::getKey, Function.identity()));
        List<String> problems = new ArrayList<>();
        List<Component> next = new ArrayList<>();
        for (ComponentDraft draft : drafts) {
            Component component = resolve(draft, saved.get(draft.key()), templates, problems);
            if (component != null) {
                next.add(component);
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidDataException(String.join("; ", problems));
        }
        for (int index = 0; index < drafts.size(); index++) {
            next.get(index).update(drafts.get(index));
        }
        components.retainAll(next);
        next.stream().filter(component -> !components.contains(component)).forEach(components::add);
        revision++;
    }

    private Component resolve(ComponentDraft draft, Component existing, Map<String, ComponentTemplate> templates,
            List<String> problems) {
        String name = draft.name();
        String label = "Component " + (name == null || name.isBlank() ? draft.key() : name) + ": ";
        int before = problems.size();
        if (draft.key() == null) {
            problems.add(label + "key is required");
        }
        if (name == null || name.isBlank()) {
            problems.add(label + "name is required");
        } else if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            problems.add(label + "name must contain at most " + MAX_NAME_LENGTH + " characters");
        }
        Component component = existing;
        TemplateContract contract;
        if (existing != null) {
            if (!existing.getTemplateKey().equals(draft.templateKey())) {
                problems.add(label + "template cannot change from %s to %s"
                        .formatted(existing.getTemplateKey(), draft.templateKey()));
                return null;
            }
            contract = TemplateContractRegistry.get(existing.getType(), existing.getContractVersion());
        } else {
            ComponentTemplate template = templates.get(draft.templateKey());
            if (template == null || !template.isEnabled()) {
                problems.add(label + "unknown or disabled template " + draft.templateKey());
                return null;
            }
            contract = template.getContract();
            component = new Component(this, draft.key(), template.getKey(), template.getType(), contract.version());
        }
        ComponentConfiguration configuration = draft.configuration();
        contract.check(configuration.profile(), configuration.parameters())
                .forEach(problem -> problems.add(label + problem));
        return problems.size() == before ? component : null;
    }

    public List<Component> getComponents() {
        return Collections.unmodifiableList(components);
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getRevision() {
        return revision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
