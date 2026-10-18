package es.codeurjc.infracture.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTests {

    @Test
    void protectsAdministratorFromDeactivationAndDeletionWhileAllowingProfileChanges() {
        User admin = new User("admin", "hash", "admin@example.com", "Admin", UserRole.ADMIN);

        assertThatThrownBy(admin::checkCanDeactivateOrDelete)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The administrator cannot be deactivated or deleted");

        admin.setDisplayName("Updated Admin");
        assertThat(admin.getDisplayName()).isEqualTo("Updated Admin");
    }

    @Test
    void permitsDeactivationAndDeletionOfOrdinaryAccounts() {
        User user = new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER);

        assertThatCode(user::checkCanDeactivateOrDelete).doesNotThrowAnyException();
    }
}
