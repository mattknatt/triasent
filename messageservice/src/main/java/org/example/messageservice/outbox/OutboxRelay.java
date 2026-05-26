package org.example.messageservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Drains unpublished outbox rows to RabbitMQ. Runs in one transaction per tick: rows are
 * only stamped {@code publishedAt} after the broker confirms every message in the batch,
 * so a crash or nack rolls back the stamps and the rows are retried next tick.
 * This is at-least-once delivery — consumers must deduplicate (inbox pattern).
 */
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void flush() {
        List<OutboxEvent> batch = repository.findUnpublishedBatch(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) {
            return;
        }

        // Publish on a single channel and block until the broker confirms all of them.
        rabbitTemplate.invoke(operations -> {
            for (OutboxEvent event : batch) {
                operations.send(event.getExchange(), event.getRoutingKey(), toMessage(event));
            }
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });

        Instant now = Instant.now();
        batch.forEach(event -> event.setPublishedAt(now)); // dirty-checked, flushed on commit
    }

    private Message toMessage(OutboxEvent event) {
        return MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("eventType", event.getEventType())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
