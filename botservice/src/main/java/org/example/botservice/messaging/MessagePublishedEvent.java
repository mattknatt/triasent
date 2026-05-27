package org.example.botservice.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Local mirror of the event messageservice publishes. Field names must match the JSON
 * keys; both sides use Spring AMQP's JacksonJsonMessageConverter, so the format is
 * symmetric (no shared class needed).
 */
public record MessagePublishedEvent(UUID id, String username, String content, Instant createdAt) {
}
