package es.codeurjc.infracture.identity.application;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import es.codeurjc.infracture.shared.storage.ObjectStorage;
import es.codeurjc.infracture.shared.storage.ObjectStorage.StoredObject;
import es.codeurjc.infracture.shared.storage.StorageUnavailableException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AvatarService implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);
    private static final String PREFIX = "avatars/";
    private static final String CONTENT_TYPE = "image/png";

    private final UserRepository users;
    private final ObjectStorage storage;
    private final TransactionTemplate transactions;

    public AvatarService(UserRepository users, ObjectStorage storage, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public record Avatar(String version, byte[] content, String contentType) {
    }

    private record Change(User user, String previousKey) {
    }

    public User replace(Long userId, byte[] content) {
        byte[] png = AvatarImage.toPng(content);
        String key = PREFIX + userId + "/" + UUID.randomUUID() + ".png";
        storage.put(key, png, CONTENT_TYPE);
        Change change;
        try {
            change = transactions.execute(status -> setKey(userId, key));
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        deleteQuietly(change.previousKey());
        return change.user();
    }

    public User remove(Long userId) {
        Change change = transactions.execute(status -> setKey(userId, null));
        deleteQuietly(change.previousKey());
        return change.user();
    }

    public Optional<Avatar> read(Long userId) {
        String key = users.findById(userId).map(User::getAvatarKey).orElse(null);
        if (key == null) {
            return Optional.empty();
        }
        return storage.get(key).map(object -> avatar(key, object));
    }

    public static String version(String key) {
        return key.substring(key.lastIndexOf('/') + 1, key.lastIndexOf('.'));
    }

    // Runs before the web server accepts requests, so no upload can be halfway through.
    @Override
    public void afterSingletonsInstantiated() {
        removeOrphans();
    }

    void removeOrphans() {
        try {
            Set<String> referenced = users.findAvatarKeys();
            for (String key : storage.keys(PREFIX)) {
                if (!referenced.contains(key)) {
                    storage.delete(key);
                    log.info("Removed unused avatar object {}", key);
                }
            }
        } catch (StorageUnavailableException e) {
            log.warn("Unused avatar objects were not removed: {}", e.getMessage());
        }
    }

    private Change setKey(Long userId, String key) {
        User user = users.findWithLockById(userId).orElseThrow();
        String previous = user.getAvatarKey();
        user.setAvatarKey(key);
        return new Change(users.saveAndFlush(user), previous);
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (StorageUnavailableException e) {
            log.warn("Avatar object {} was not deleted; the next startup removes it", key);
        }
    }

    private static Avatar avatar(String key, StoredObject object) {
        return new Avatar(version(key), object.content(), object.contentType());
    }
}
