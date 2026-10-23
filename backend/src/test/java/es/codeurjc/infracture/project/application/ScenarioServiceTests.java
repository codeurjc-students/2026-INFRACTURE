package es.codeurjc.infracture.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.domain.Scenario;
import es.codeurjc.infracture.project.persistence.ScenarioRepository;
import es.codeurjc.infracture.shared.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class ScenarioServiceTests {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final String OWNER_EMAIL = "alice@example.com";

    private final ScenarioRepository repository = mock(ScenarioRepository.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final ScenarioService service = new ScenarioService(repository, projectService, VALIDATORS.getValidator());
    private final Project project = new Project(new User("alice", "hash", OWNER_EMAIL, "Alice", UserRole.USER),
            "Cache lab", null);

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATORS.close();
    }

    @BeforeEach
    void identifyOwnProject() {
        ReflectionTestUtils.setField(project, "id", 5L);
        when(projectService.getOwnProject(OWNER_EMAIL, 5L)).thenReturn(project);
        when(repository.save(any(Scenario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveAndFlush(any(Scenario.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsEmptyScenarioInOwnProjectAtInitialRevision() {
        Scenario created = service.createScenario(OWNER_EMAIL, 5L, "  Cold cache  ", "   ");

        assertThat(created.getProject()).isSameAs(project);
        assertThat(created.getName()).isEqualTo("Cold cache");
        assertThat(created.getDescription()).isNull();
        assertThat(created.getRevision()).isEqualTo(1);
        verify(repository).save(created);
    }

    @Test
    void rejectsScenarioInProjectOfAnotherAccount() {
        when(projectService.getOwnProject(OWNER_EMAIL, 9L)).thenThrow(new NotFoundException("Project not found"));

        assertThatThrownBy(() -> service.createScenario(OWNER_EMAIL, 9L, "Cold cache", null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
        assertThatThrownBy(() -> service.getScenarios(OWNER_EMAIL, 9L, PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsInvalidDetailsWithoutSaving() {
        assertThatThrownBy(() -> service.createScenario(OWNER_EMAIL, 5L, " ", null))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Scenario name is required");
        assertThatThrownBy(() -> service.createScenario(OWNER_EMAIL, 5L, "n".repeat(31), null))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("at most 30 characters");
        assertThatThrownBy(() -> service.createScenario(OWNER_EMAIL, 5L, "Lab", "d".repeat(281)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("at most 280 characters");
        verify(repository, never()).save(any());
    }

    @Test
    void listsScenariosOfOwnProject() {
        PageRequest pageable = PageRequest.of(1, 10);
        Page<Scenario> page = new PageImpl<>(List.of(new Scenario(project, "Cold cache", null)), pageable, 11);
        when(repository.findAllByProjectIdOrderByCreatedAtDescIdDesc(5L, pageable)).thenReturn(page);

        assertThat(service.getScenarios(OWNER_EMAIL, 5L, pageable)).isSameAs(page);
    }

    @Test
    void treatsScenariosOutsideTheProjectAsNotFound() {
        when(repository.findByIdAndProjectId(99L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getScenario(OWNER_EMAIL, 5L, 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Scenario not found");
        assertThatThrownBy(() -> service.updateScenario(OWNER_EMAIL, 5L, 99L, "Taken over", null))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void renamesScenarioKeepingProjectAndRevision() {
        Scenario existing = new Scenario(project, "Old name", "Old description");
        when(repository.findByIdAndProjectId(3L, 5L)).thenReturn(Optional.of(existing));

        Scenario updated = service.updateScenario(OWNER_EMAIL, 5L, 3L, " New name ", "");

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getDescription()).isNull();
        assertThat(updated.getProject()).isSameAs(project);
        assertThat(updated.getRevision()).isEqualTo(1);
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void rejectsInvalidRenameWithoutSaving() {
        when(repository.findByIdAndProjectId(3L, 5L)).thenReturn(Optional.of(new Scenario(project, "Lab", null)));

        assertThatThrownBy(() -> service.updateScenario(OWNER_EMAIL, 5L, 3L, "", null))
                .isInstanceOf(ConstraintViolationException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
