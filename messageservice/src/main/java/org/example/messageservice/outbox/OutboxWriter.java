package org.example.messageservice.outbox;

import lombok.RequiredArgsConstructor;
import org.example.messageservice.messaging.MessagePublishedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Persists a domain event into the outbox table. Must be called from within the
 * business transaction so the event row commits atomically with the business write.
 * Serialization reuses the project's configured AMQP {@link MessageConverter}, so the
 * stored payload is byte-for-byte what the relay will put on the wire.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxRepository repository;
    private final MessageConverter messageConverter;

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routing-key}")
    private String routingKey;

    public void add(MessagePublishedEvent event) {
        Message message = messageConverter.toMessage(event, new MessageProperties());

        OutboxEvent row = new OutboxEvent();
        row.setEventType(event.getClass().getSimpleName());
        row.setExchange(exchange);
        row.setRoutingKey(routingKey);
        row.setPayload(new String(message.getBody(), StandardCharsets.UTF_8));
        repository.save(row);
    }
}
