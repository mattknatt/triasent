package org.example.messageservice.messaging;

import java.time.Instant;
import java.util.UUID;

public record MessagePublishedEvent(UUID id, String username, String content, Instant createdAt) {
}
