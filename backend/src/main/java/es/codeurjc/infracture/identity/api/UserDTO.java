package es.codeurjc.infracture.identity.api;

import es.codeurjc.infracture.identity.domain.UserRole;
import java.time.Instant;

public record UserDTO(
        Long id,
        String username,
        String displayName,
        String email,
        UserRole role,
        boolean active,
        // Null when the account has no avatar. The storage key stays in the backend.
        String avatarUrl,
        Instant createdAt,
        Instant updatedAt) {
}
