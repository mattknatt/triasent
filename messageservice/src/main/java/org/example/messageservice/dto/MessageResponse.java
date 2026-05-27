package org.example.messageservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A message plus the author's role, which is fetched from userservice over gRPC at read
 * time. {@code authorRole} is null when the author has no userservice profile (e.g. the bot).
 */
public record MessageResponse(
        UUID id,
        String username,
        String content,
        Instant createdAt,
        String authorRole
) {
}
