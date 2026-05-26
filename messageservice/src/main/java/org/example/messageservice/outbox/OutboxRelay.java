package org.example.messageservice.outbox;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 *
 * <p>The batch is loaded under a short transaction and the {@code PESSIMISTIC_WRITE}
 * locks are released before any RabbitMQ I/O; outcomes are persisted in a second short
 * transaction. This keeps DB locks and the pooled connection off the network path.
 */
@Slf4j
@Component
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final long CONFIRM_TIMEOUT_MS = 5_000;
    private static final int MAX_ERROR_LEN = 1000;

    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.outbox.max-attempts:5}")
    private int maxAttempts;

    public OutboxRelay(OutboxRepository repository,
                       RabbitTemplate rabbitTemplate,
                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void flush() {
        // 1) Short transaction: lock + load a batch, then commit so the PESSIMISTIC_WRITE
        //    locks and the DB connection are released before we touch the network.
        List<OutboxEvent> batch = transactionTemplate.execute(status ->
                repository.findUnpublishedBatch(PageRequest.of(0, BATCH_SIZE)));
        if (batch == null || batch.isEmpty()) {
            return;
        }

        // 2) Publish each event outside any transaction and wait for the broker confirm.
        //    The entities are detached here; publishOne only mutates their in-memory state.
        for (OutboxEvent event : batch) {
            publishOne(event);
        }

        // 3) Separate short transaction: persist the published/failed outcomes.
        transactionTemplate.executeWithoutResult(status -> repository.saveAll(batch));
    }

    private void publishOne(OutboxEvent event) {
        try {
            CorrelationData correlation = new CorrelationData(event.getId().toString());
            rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), toMessage(event), correlation);

            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (confirm == null) {
                recordFailure(event, "confirm timeout");
            } else if (!confirm.isAck()) {
                recordFailure(event, "broker nack: " + confirm.getReason());
            } else if (correlation.getReturned() != null) {
                // Acked by the broker but matched no queue -> would otherwise be dropped.
                recordFailure(event, "unroutable: " + correlation.getReturned().getReplyText());
            } else {
                event.setPublishedAt(Instant.now());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordFailure(event, e.getMessage());
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
