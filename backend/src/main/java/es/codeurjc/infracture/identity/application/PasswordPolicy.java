package es.codeurjc.infracture.identity.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 15;
    public static final int MAX_LENGTH = 128;

    private static final Pattern INVALID_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cs}]");
    private static final Pattern REQUIRED_CHARACTERS = Pattern.compile(
            "(?=.*\\p{javaUpperCase})(?=.*\\p{javaLowerCase})(?=.*\\p{javaDigit})(?=.*[\\p{P}\\p{S}]).*",
            Pattern.DOTALL);

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        ClassPathResource resource = new ClassPathResource("security/common-passwords.txt.gz");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(resource.getInputStream()), StandardCharsets.UTF_8))) {
            commonPasswords = reader.lines().collect(Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the common password blocklist", exception);
        }
    }

    public String validate(String password, String username, String email, String displayName) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }

        String normalized = Normalizer.normalize(password, Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());

        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException("Password must contain between 15 and 128 characters");
        }
        if (INVALID_CHARACTERS.matcher(normalized).find()) {
            throw new IllegalArgumentException("Password must contain valid Unicode without control characters");
        }

        String candidate = normalized.strip().toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(candidate)
                || candidate.equals(comparisonValue(username))
                || candidate.equals(comparisonValue(email))
                || candidate.equals(comparisonValue(displayName))) {
            throw new IllegalArgumentException("Choose a password that is not common or based on your account details");
        }

        if (!REQUIRED_CHARACTERS.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Password must contain an uppercase letter, a lowercase letter, a number and a special character");
        }
        return normalized;
    }

    private String comparisonValue(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
    }
}
