package org.example.messageservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Drains unpublished outbox rows to RabbitMQ, one row at a time so a single failure
 * neither rolls back the whole batch nor blocks the rows behind it. A row is stamped
 * {@code publishedAt} only after the broker confirms it (at-least-once — consumers must
 * deduplicate). A row that keeps failing is retried up to {@code maxAttempts}, then
 * parked ({@code failedAt} set) so it stops blocking the pipeline — the producer-side
 * equivalent of a dead-letter queue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final long CONFIRM_TIMEOUT_MS = 5_000;
    private static final int MAX_ERROR_LEN = 1000;

    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.outbox.max-attempts:5}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void flush() {
        List<OutboxEvent> batch = repository.findUnpublishedBatch(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : batch) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            CorrelationData correlation = new CorrelationData(event.getId().toString());
            rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), toMessage(event), correlation);

            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (confirm != null && confirm.isAck()) {
                event.setPublishedAt(Instant.now()); // dirty-checked, flushed on commit
            } else {
                recordFailure(event, confirm == null ? "confirm timeout" : "broker nack: " + confirm.getReason());
            }
        } catch (Exception e) {
            recordFailure(event, e.getMessage());
        }
    }

    private void recordFailure(OutboxEvent event, String reason) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(truncate(reason));
        if (event.getAttempts() >= maxAttempts) {
            event.setFailedAt(Instant.now()); // park it: no longer polled
            log.error("Outbox event {} parked after {} attempts: {}", event.getId(), event.getAttempts(), reason);
        } else {
            log.warn("Outbox event {} publish attempt {} failed: {}", event.getId(), event.getAttempts(), reason);
        }
    }

    private Message toMessage(OutboxEvent event) {
        return MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("eventType", event.getEventType())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_ERROR_LEN ? reason : reason.substring(0, MAX_ERROR_LEN);
    }
}
