package org.example.messageservice.service;

import lombok.RequiredArgsConstructor;
import org.example.messageservice.messaging.MessagePublishedEvent;
import org.example.messageservice.model.MessageEntity;
import org.example.messageservice.outbox.OutboxWriter;
import org.example.messageservice.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository repository;
    private final OutboxWriter outbox;

    @Transactional
    public MessageEntity post(String username, String content) {
        MessageEntity entity = new MessageEntity();
        entity.setUsername(username);
        entity.setContent(content);
        MessageEntity saved = repository.save(entity);

        // Same transaction as the message insert -> the event can never be lost or
        // emitted for a message that wasn't persisted. The relay publishes it later.
        outbox.add(new MessagePublishedEvent(
                saved.getId(), saved.getUsername(), saved.getContent(), saved.getCreatedAt()));

        return saved;
    }

    public List<MessageEntity> all() {
        return repository.findAllByOrderByCreatedAtDesc();
    }
}
