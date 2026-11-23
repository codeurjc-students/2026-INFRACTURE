package es.codeurjc.infracture.identity.application;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import es.codeurjc.infracture.identity.persistence.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

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

    private String normalizeIdentifier(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String value) {
        return value == null ? null : Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }
}
