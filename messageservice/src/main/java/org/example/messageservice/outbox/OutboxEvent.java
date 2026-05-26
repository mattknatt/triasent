package org.example.messageservice.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
