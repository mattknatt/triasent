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

    /**
     * Atomically claims an event id. Returns {@code true} only if it wasn't already
     * present, so a duplicate or concurrent delivery can't both pass the check.
     */
    public boolean tryMarkProcessing(UUID id) {
        return seen.asMap().putIfAbsent(id, Boolean.TRUE) == null;
    }

    /** Releases a claim so a failed delivery can be retried instead of skipped. */
    public void release(UUID id) {
        seen.asMap().remove(id);
    }
}
