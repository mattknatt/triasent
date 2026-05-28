package org.example.messageservice.messaging;

import java.time.Instant;
import java.util.UUID;

public record MessagePublishedEvent(UUID id, UUID userId, String content, Instant createdAt) {
}
