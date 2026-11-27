package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.ExecutionPlan;
import es.codeurjc.infracture.execution.domain.ExecutionPlan.Node;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

// The temporary passwords of an execution's nodes. Each one is derived from a key that lives only in this
// backend's memory, the plan's project and the node's key, so it is never stored and every Compose command
// of the same run gets the same value. A restart draws a new key; it also interrupts the open execution, and
// cleaning up needs no password.
@Component
class ExecutionCredentials {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "INFRACTURE_SECRET_";

    private final SecretKeySpec key;

    ExecutionCredentials() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        this.key = new SecretKeySpec(bytes, ALGORITHM);
    }

    static String variable(UUID node) {
        return PREFIX + node.toString().replace("-", "");
    }

    Map<String, String> environment(ExecutionPlan plan) {
        Map<String, String> environment = new LinkedHashMap<>();
        for (Node node : plan.nodes()) {
            for (UUID owner : node.wiring().secrets().values()) {
                environment.computeIfAbsent(variable(owner), _ -> password(plan.project(), owner));
            }
        }
        return environment;
    }

    // URL-safe, so it fits in a connection string without escaping.
    private String password(String project, UUID node) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((project + "/" + node).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("The JVM cannot compute " + ALGORITHM, exception);
        }
    }
}
