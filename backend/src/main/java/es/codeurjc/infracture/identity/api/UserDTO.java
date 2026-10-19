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
        String avatarKey,
        Instant createdAt,
        Instant updatedAt) {
}
