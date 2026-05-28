package org.example.messageservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A message plus the author's display info, which is fetched from userservice over gRPC
 * at read time. {@code authorRole} is null when the author has no userservice profile
 * (e.g. the bot). {@code userId} is the stable identifier — clients that need to
 * classify rows (e.g. the bot mapping rows to LLM roles) should key off it rather than
 * the display name.
 */
public record MessageResponse(
        UUID id,
        UUID userId,
        String username,
        String content,
        Instant createdAt,
        String authorRole
) {
}
