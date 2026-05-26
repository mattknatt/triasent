package org.example.botservice.messaging;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Remembers recently-processed event ids so at-least-once redeliveries don't trigger a
 * second LLM call / duplicate reply (the inbox pattern, in-memory).
 *
 * <p>MVP trade-off: state is lost on restart and not shared across replicas. A DB-backed
 * inbox table would make dedup durable and multi-replica-safe.
 */
@Component
public class ProcessedEventStore {

    private final Cache<UUID, Boolean> seen = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public boolean alreadyProcessed(UUID id) {
        return seen.getIfPresent(id) != null;
    }

    public void markProcessed(UUID id) {
        seen.put(id, Boolean.TRUE);
    }
}
