package org.example.botservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.botservice.model.ChatRequest;
import org.example.botservice.model.ChatResponse;
import org.example.botservice.service.ChatService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consumes message.published events, generates an LLM reply, and posts it back as "bot".
 * Skips the bot's own messages (loop prevention) and already-seen events (dedup).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventListener {

    private final ChatService chatService;
    private final BotReplyClient botReplyClient;
    private final ProcessedEventStore processedEvents;

    @Value("${app.bot.user-id}")
    private java.util.UUID botUserId;

    @RabbitListener(queues = "${app.messaging.queue}")
    public void onMessagePublished(MessagePublishedEvent event) {
        // Loop prevention: ignore the bot's own messages so its replies don't re-trigger it.
        if (botUserId.equals(event.userId())) {
            log.debug("Skipping bot's own message {}", event.id());
            return;
        }

        // Idempotency: atomically claim the event id. A duplicate or concurrent delivery
        // that can't claim it is skipped. (Server-side idempotency is the durable backstop
        // across restarts; this just avoids redundant LLM calls.)
        if (!processedEvents.tryMarkProcessing(event.id())) {
            log.debug("Skipping already-claimed event {}", event.id());
            return;
        }

        try {
            log.info("Received message.published from user '{}'", event.userId());
            log.debug("Received message.published from user '{}': {}", event.userId(), event.content());

            // sessionId = the user's UUID: ChatService uses it as the ownerUserId when
            // pulling the conversation transcript from messageservice.
            ChatRequest request = new ChatRequest(event.content(), event.userId().toString());
            ChatResponse response = chatService.chat(request);

            log.info("Bot reply for user '{}' (session {})", event.userId(), response.sessionId());
            log.debug("Bot reply for user '{}' (session {}): {}",
                    event.userId(), response.sessionId(), response.response());

            // Post the reply back as the bot, attributed to the original user's conversation
            // so only they see it; the source event id is the idempotency key.
            botReplyClient.postReply(response.response(), event.userId(), event.id().toString());
        } catch (RuntimeException e) {
            // Failed -> release the claim so the redelivery is retried instead of skipped.
            processedEvents.release(event.id());
            throw e;
        }
    }
}
