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

    public User registerUser(User user) {
        return createUser(user, UserRole.USER);
    }

    public void initializeAdmin(User user) {
        if (!userRepository.existsByRole(UserRole.ADMIN)) {
            createUser(user, UserRole.ADMIN);
        }
    }

    private User createUser(User user, UserRole role) {
        if (user == null || user.getId() != null) {
            throw new IllegalArgumentException("Registration requires a new user without an ID");
        }

        user.setUsername(normalizeIdentifier(user.getUsername()));
        user.setEmail(user.getEmail() == null ? null : user.getEmail().trim());
        user.setDisplayName(normalizeDisplayName(user.getDisplayName()));
        user.setRole(role);
        user.setActive(true);
        user.setAvatarKey(null);

        Set<ConstraintViolation<User>> violations = validator.validate(user);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        String password = passwordPolicy.validate(user.getEncodedPassword(), user.getUsername(),
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
