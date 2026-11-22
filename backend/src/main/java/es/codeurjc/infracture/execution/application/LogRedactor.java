package es.codeurjc.infracture.execution.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Cleans a log line before it is stored or shown (#92). The backend's own secrets are replaced wherever they
// appear, and so are the values that usually follow a secret's name, bearer tokens and JWTs. Then the line is
// cut to a bounded length.
@Component
class LogRedactor {

    static final int MAX_LINE_LENGTH = 500;
    static final String REDACTED = "[redacted]";
    // Shorter values would replace ordinary words.
    private static final int MIN_SECRET_LENGTH = 4;
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key)(\\s*[=:]\\s*)(\"[^\"]*\"|'[^']*'|\\S+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(bearer\\s+)[A-Za-z0-9._~+/-]+=*");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    private final List<String> secrets = new ArrayList<>();

    LogRedactor(@Value("${spring.datasource.password:}") String databasePassword,
            @Value("${infracture.admin.password:}") String adminPassword) {
        for (String secret : List.of(databasePassword, adminPassword)) {
            if (secret.length() >= MIN_SECRET_LENGTH) {
                secrets.add(secret);
            }
        }
    }

    String clean(String line) {
        String clean = line;
        for (String secret : secrets) {
            clean = clean.replace(secret, REDACTED);
        }
        clean = NAMED_SECRET.matcher(clean).replaceAll("$1$2" + REDACTED);
        clean = BEARER.matcher(clean).replaceAll("$1" + REDACTED);
        clean = JWT.matcher(clean).replaceAll(REDACTED);
        return clean.length() > MAX_LINE_LENGTH ? clean.substring(0, MAX_LINE_LENGTH) : clean;
    }
}
