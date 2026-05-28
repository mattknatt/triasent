package org.example.messageservice.service;

import lombok.RequiredArgsConstructor;
import org.example.messageservice.messaging.MessagePublishedEvent;
import org.example.messageservice.model.MessageEntity;
import org.example.messageservice.outbox.OutboxWriter;
import org.example.messageservice.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository repository;
    private final OutboxWriter outbox;

    @Transactional
    public MessageEntity post(UUID userId, UUID ownerUserId, String content, String idempotencyKey) {
        // Idempotency: a repeated create with the same key returns the existing message
        // and skips both the insert and the outbox event, so retried/redelivered posts
        // produce no duplicate reply downstream. (The unique column is the hard backstop.)
        if (idempotencyKey != null) {
            Optional<MessageEntity> existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        MessageEntity entity = new MessageEntity();
        entity.setUserId(userId);
        entity.setOwnerUserId(ownerUserId);
        entity.setContent(content);
        entity.setIdempotencyKey(idempotencyKey);
        MessageEntity saved = repository.save(entity);

        // Same transaction as the message insert -> the event can never be lost or
        // emitted for a message that wasn't persisted. The relay publishes it later.
        outbox.add(new MessagePublishedEvent(
                saved.getId(), saved.getUserId(), saved.getContent(), saved.getCreatedAt()));

        return saved;
    }

    public List<MessageEntity> forOwner(UUID ownerUserId) {
        return repository.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
    }
}
