package es.codeurjc.infracture.identity.application;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import es.codeurjc.infracture.shared.InvalidDataException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final PasswordPolicy passwordPolicy;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            Validator validator, PasswordPolicy passwordPolicy) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.passwordPolicy = passwordPolicy;
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeIdentifier(email))
                .orElseThrow();
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsernameIgnoreCase(normalizeIdentifier(username));
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmailIgnoreCase(normalizeIdentifier(email));
    }

    public User registerUser(String username, String displayName, String email, String password) {
        return createUser(username, displayName, email, password, UserRole.USER);
    }

    public void initializeAdmin(String username, String displayName, String email, String password) {
        if (!userRepository.existsByRole(UserRole.ADMIN)) {
            createUser(username, displayName, email, password, UserRole.ADMIN);
        }
    }

    private User createUser(String username, String displayName, String email, String rawPassword, UserRole role) {
        // The entity receives only the hash, once the account and the password are valid.
        User user = new User(normalizeIdentifier(username), null, email == null ? null : email.trim(),
                normalizeDisplayName(displayName), role);

        Set<ConstraintViolation<User>> violations = validator.validate(user);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        String password = passwordPolicy.validate(rawPassword, user.getUsername(),
                user.getEmail(), user.getDisplayName());

        if (existsByUsername(user.getUsername())) {
            throw new UserAlreadyExistsException("Username is already in use");
        }
        if (existsByEmail(user.getEmail())) {
            throw new UserAlreadyExistsException("Email is already in use");
        }

        user.setEncodedPassword(passwordEncoder.encode(password));
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            if (existsByUsername(user.getUsername())) {
                throw new UserAlreadyExistsException("Username is already in use");
            }
            if (existsByEmail(user.getEmail())) {
                throw new UserAlreadyExistsException("Email is already in use");
            }
            throw exception;
        }
    }

    // A field that is not sent keeps its value. The sessions stay valid.
    @Transactional
    public User updateProfile(Long userId, String displayName, String username) {
        User user = lockedUser(userId);
        if (displayName != null) {
            user.setDisplayName(normalizeDisplayName(displayName));
        }
        if (username != null) {
            user.setUsername(normalizeIdentifier(username));
        }
        return saveValid(user);
    }

    // The password is checked first, so a stolen session cannot find out which emails exist.
    @Transactional
    public User changeEmail(Long userId, String email, String currentPassword) {
        User user = lockedUser(userId);
        requireCurrentPassword(user, currentPassword);
        String newEmail = email == null ? null : email.trim();
        if (!user.getEmail().equals(newEmail)) {
            user.setEmail(newEmail);
            user.revokeSessions();
        }
        return saveValid(user);
    }

    @Transactional
    public User changePassword(Long userId, String currentPassword, String newPassword) {
        User user = lockedUser(userId);
        requireCurrentPassword(user, currentPassword);
        String password = passwordPolicy.validate(newPassword, user.getUsername(), user.getEmail(),
                user.getDisplayName());
        user.setEncodedPassword(passwordEncoder.encode(password));
        user.revokeSessions();
        return saveValid(user);
    }

    public boolean isSessionCurrent(Long userId, int tokenVersion) {
        return userRepository.existsByIdAndActiveTrueAndTokenVersion(userId, tokenVersion);
    }

    // Without the lock, a concurrent save could write back an older token version.
    private User lockedUser(Long userId) {
        return userRepository.findWithLockById(userId).orElseThrow();
    }

    // Any exception rolls the transaction back, so a rejected change is never stored.
    private User saveValid(User user) {
        Set<ConstraintViolation<User>> violations = validator.validate(user);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            String cause = exception.getMostSpecificCause().getMessage();
            if (cause.contains("uk_users_username_normalized")) {
                throw new UserAlreadyExistsException("Username is already in use");
            }
            if (cause.contains("uk_users_email_normalized")) {
                throw new UserAlreadyExistsException("Email is already in use");
            }
            throw exception;
        }
    }

    // Not an authentication error: the session is fine, and the client would try to renew it on a 401.
    private void requireCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null || !passwordEncoder.matches(
                Normalizer.normalize(currentPassword, Normalizer.Form.NFC), user.getEncodedPassword())) {
            throw new InvalidDataException("Current password is incorrect");
        }
    }

    private String normalizeIdentifier(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String value) {
        return value == null ? null : Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }
}
