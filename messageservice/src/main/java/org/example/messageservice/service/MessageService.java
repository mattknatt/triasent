package org.example.messageservice.service;

import lombok.RequiredArgsConstructor;
import org.example.messageservice.messaging.MessagePublishedEvent;
import org.example.messageservice.messaging.MessagePublisher;
import org.example.messageservice.model.MessageEntity;
import org.example.messageservice.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository repository;
    private final MessagePublisher publisher;

    public MessageEntity post(String username, String content) {
        MessageEntity entity = new MessageEntity();
        entity.setUsername(username);
        entity.setContent(content);
        MessageEntity saved = repository.save(entity);

        publisher.publish(new MessagePublishedEvent(
                saved.getId(), saved.getUsername(), saved.getContent(), saved.getCreatedAt()));

        return saved;
    }

    public List<MessageEntity> all() {
        return repository.findAllByOrderByCreatedAtDesc();
    }
}
