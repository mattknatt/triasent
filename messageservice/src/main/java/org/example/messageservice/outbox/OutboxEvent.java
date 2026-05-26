package org.example.messageservice.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending domain event, written in the same transaction as the business data it
 * describes. The {@link OutboxRelay} drains rows where {@code publishedAt} is null and
 * publishes them to RabbitMQ, then stamps {@code publishedAt}.
 */
@Entity
@Table(name = "outbox", indexes = @Index(name = "idx_outbox_pub", columnList = "publishedAt, createdAt"))
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String exchange;

    @Column(nullable = false)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    /** Null until the relay has had the broker confirm receipt. */
    private Instant publishedAt;

    /** Failed publish attempts so far. Once it hits the cap the row is parked. */
    @ColumnDefault("0")
    @Column(nullable = false)
    private int attempts = 0;

    /** Last failure reason, for diagnosing parked rows. */
    @Column(columnDefinition = "text")
    private String lastError;

    /**
     * Set when the relay gives up after {@code attempts} reaches the cap. A non-null
     * value means this row is the producer-side "dead letter" — it will no longer be
     * polled and needs manual inspection / replay.
     */
    private Instant failedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
