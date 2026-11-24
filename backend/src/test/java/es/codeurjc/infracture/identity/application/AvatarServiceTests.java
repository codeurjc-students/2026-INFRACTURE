package es.codeurjc.infracture.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.identity.TestImages;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import es.codeurjc.infracture.shared.InvalidDataException;
import es.codeurjc.infracture.shared.storage.ObjectStorage;
import es.codeurjc.infracture.shared.storage.ObjectStorage.StoredObject;
import es.codeurjc.infracture.shared.storage.StorageUnavailableException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;

class AvatarServiceTests {

    private static final Long USER_ID = 42L;
    private static final String OLD_KEY = "avatars/42/old.png";

    private final UserRepository users = mock(UserRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final AvatarService service = new AvatarService(users, storage, mock(PlatformTransactionManager.class));
    private final User user = new User("alice", "hash", "alice@example.com", "Alice", UserRole.USER);

    @BeforeEach
    void storedAccountWithAnAvatar() {
        user.setAvatarKey(OLD_KEY);
        when(users.findWithLockById(USER_ID)).thenReturn(Optional.of(user));
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(users.saveAndFlush(user)).thenReturn(user);
    }

    @Test
    void storesTheNewImageBeforeSwitchingAndDeletesTheOldOneAfter() {
        User updated = service.replace(USER_ID, TestImages.jpeg(300, 300));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(storage, users);
        order.verify(storage).put(key.capture(), any(), eq("image/png"));
        order.verify(users).saveAndFlush(user);
        order.verify(storage).delete(OLD_KEY);
        assertThat(key.getValue()).matches("avatars/42/[0-9a-f-]{36}\\.png");
        assertThat(updated.getAvatarKey()).isEqualTo(key.getValue());
    }

    @Test
    void keepsThePreviousImageWhenTheStorageFails() {
        doThrow(new StorageUnavailableException(new RuntimeException("down")))
                .when(storage).put(anyString(), any(), anyString());

        assertThatThrownBy(() -> service.replace(USER_ID, TestImages.png(200, 200)))
                .isInstanceOf(StorageUnavailableException.class);

        verify(users, never()).saveAndFlush(any());
        verify(storage, never()).delete(anyString());
        assertThat(user.getAvatarKey()).isEqualTo(OLD_KEY);
    }

    @Test
    void deletesTheNewImageAndKeepsTheOldOneWhenTheDatabaseFails() {
        when(users.saveAndFlush(user)).thenThrow(new DataAccessResourceFailureException("database down"));

        assertThatThrownBy(() -> service.replace(USER_ID, TestImages.png(200, 200)))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(storage).delete(startsWith("avatars/42/"));
        verify(storage, never()).delete(OLD_KEY);
    }

    @Test
    void succeedsWhenTheOldImageCannotBeDeleted() {
        doThrow(new StorageUnavailableException(new RuntimeException("down"))).when(storage).delete(OLD_KEY);

        User updated = service.replace(USER_ID, TestImages.png(200, 200));

        assertThat(updated.getAvatarKey()).startsWith("avatars/42/").isNotEqualTo(OLD_KEY);
    }

    @Test
    void rejectsAnInvalidImageWithoutTouchingStorageOrTheAccount() {
        assertThatThrownBy(() -> service.replace(USER_ID, TestImages.png(300, 200)))
                .isInstanceOf(InvalidDataException.class);

        verifyNoInteractions(storage);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void removesTheAvatarAndThenItsObject() {
        User updated = service.remove(USER_ID);

        InOrder order = inOrder(users, storage);
        order.verify(users).saveAndFlush(user);
        order.verify(storage).delete(OLD_KEY);
        assertThat(updated.getAvatarKey()).isNull();
    }

    @Test
    void readsTheCurrentImageWithItsVersion() {
        when(storage.get(OLD_KEY)).thenReturn(Optional.of(new StoredObject(new byte[] { 1 }, "image/png")));

        assertThat(service.read(USER_ID)).hasValueSatisfying(avatar -> {
            assertThat(avatar.version()).isEqualTo("old");
            assertThat(avatar.content()).containsExactly(1);
        });
    }

    @Test
    void findsNoAvatarForAnAccountWithoutOne() {
        user.setAvatarKey(null);

        assertThat(service.read(USER_ID)).isEmpty();
        verifyNoInteractions(storage);
    }

    @Test
    void removesOnlyObjectsThatNoAccountReferences() {
        when(users.findAvatarKeys()).thenReturn(Set.of(OLD_KEY));
        when(storage.keys("avatars/")).thenReturn(List.of(OLD_KEY, "avatars/7/orphan.png"));

        service.removeOrphans();

        verify(storage).delete("avatars/7/orphan.png");
        verify(storage, never()).delete(OLD_KEY);
    }

    @Test
    void startsEvenIfTheStorageIsUnavailable() {
        when(storage.keys("avatars/")).thenThrow(new StorageUnavailableException(new RuntimeException("down")));

        service.removeOrphans();

        verify(storage, never()).delete(anyString());
    }
}
