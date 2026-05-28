package org.example.messageservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String username;

    /**
     * Whose conversation this message belongs to. For human posts this equals the author
     * ({@link #username}); for bot replies it is the human the bot is replying to. The
     * GET /messages list filters by this so each user only sees their own thread.
     * Nullable in the schema so Hibernate's ddl-auto=update can add it without rewriting
     * existing rows — the controller/service always populate it for new rows.
     */
    @Column(name = "owner_username")
    private String ownerUsername;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Optional caller-supplied idempotency token (unique). A repeated create with the same
     * key returns the existing message, so retried/redelivered posts don't duplicate.
     * Null for clients that don't send one (e.g. human users).
     */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
