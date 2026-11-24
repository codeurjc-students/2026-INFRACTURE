package es.codeurjc.infracture.identity.persistence;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByRole(UserRole role);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByIdAndActiveTrueAndTokenVersion(Long id, int tokenVersion);

    // Account changes run one at a time, so none overwrites the version or the fields of another.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockById(Long id);

    @Query("select u.avatarKey from User u where u.avatarKey is not null")
    Set<String> findAvatarKeys();

}
