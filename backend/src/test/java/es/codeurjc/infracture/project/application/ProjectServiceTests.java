package es.codeurjc.infracture.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.identity.application.UserService;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.project.domain.Project;
import es.codeurjc.infracture.project.persistence.ProjectRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ProjectServiceTests {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final String OWNER_EMAIL = "alice@example.com";

    private final ProjectRepository repository = mock(ProjectRepository.class);
    private final UserService userService = mock(UserService.class);
    private final ProjectService service = new ProjectService(repository, userService, VALIDATORS.getValidator());
    private final User owner = new User("alice", "hash", OWNER_EMAIL, "Alice", UserRole.USER);

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATORS.close();
    }

    @BeforeEach
    void identifyOwner() {
        owner.setId(7L);
        when(userService.getUserByEmail(OWNER_EMAIL)).thenReturn(owner);
        when(repository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveAndFlush(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsProjectOwnedBySessionUserWithNormalizedDetails() {
        Project created = service.createProject(OWNER_EMAIL, "  Cache lab  ", "   ");

        assertThat(created.getOwner()).isSameAs(owner);
        assertThat(created.getName()).isEqualTo("Cache lab");
        assertThat(created.getDescription()).isNull();
        verify(repository).save(created);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void rejectsBlankNameWithoutSaving(String name) {
        assertThatThrownBy(() -> service.createProject(OWNER_EMAIL, name, "Description"))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Project name is required");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsNameAndDescriptionAboveTheirLimits() {
        assertThatThrownBy(() -> service.createProject(OWNER_EMAIL, "n".repeat(31), null))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("at most 30 characters");
        assertThatThrownBy(() -> service.createProject(OWNER_EMAIL, "Lab", "d".repeat(281)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("at most 280 characters");
        verify(repository, never()).save(any());
    }

    @Test
    void countsCharactersRatherThanUtf16UnitsAtTheLimits() {
        Project created = service.createProject(OWNER_EMAIL, "🚀".repeat(30), "é".repeat(280));

        assertThat(created.getName()).hasSize(60);
        verify(repository).save(created);
    }

    @Test
    void listsOnlyProjectsOfTheSessionUser() {
        PageRequest pageable = PageRequest.of(1, 10);
        Page<Project> page = new PageImpl<>(java.util.List.of(new Project(owner, "Lab", null)), pageable, 11);
        when(repository.findAllByOwnerIdOrderByCreatedAtDescIdDesc(7L, pageable)).thenReturn(page);

        assertThat(service.getOwnProjects(OWNER_EMAIL, pageable)).isSameAs(page);
    }

    @Test
    void treatsProjectsOfOtherAccountsAsNotFound() {
        when(repository.findByIdAndOwnerId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnProject(OWNER_EMAIL, 99L))
                .isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> service.updateProject(OWNER_EMAIL, 99L, "Taken over", null))
                .isInstanceOf(ProjectNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updatesNameAndDescriptionOfOwnProject() {
        Project existing = new Project(owner, "Old name", "Old description");
        when(repository.findByIdAndOwnerId(3L, 7L)).thenReturn(Optional.of(existing));

        Project updated = service.updateProject(OWNER_EMAIL, 3L, " New name ", " New description ");

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getOwner()).isSameAs(owner);
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void rejectsInvalidUpdateWithoutSaving() {
        when(repository.findByIdAndOwnerId(3L, 7L)).thenReturn(Optional.of(new Project(owner, "Lab", null)));

        assertThatThrownBy(() -> service.updateProject(OWNER_EMAIL, 3L, " ", null))
                .isInstanceOf(ConstraintViolationException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
