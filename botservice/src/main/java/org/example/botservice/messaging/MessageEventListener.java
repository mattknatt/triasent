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

    private static final String PERSONALITY = "helper";

    private final ChatService chatService;
    private final BotReplyClient botReplyClient;
    private final ProcessedEventStore processedEvents;

    @Value("${app.bot.username}")
    private String botUsername;

    @RabbitListener(queues = "${app.messaging.queue}")
    public void onMessagePublished(MessagePublishedEvent event) {
        // Loop prevention: ignore the bot's own messages so its replies don't re-trigger it.
        if (botUsername.equalsIgnoreCase(event.username())) {
            log.debug("Skipping bot's own message {}", event.id());
            return;
        }

        // Idempotency: a redelivered event must not trigger a second LLM call / reply.
        if (processedEvents.alreadyProcessed(event.id())) {
            log.debug("Skipping already-processed event {}", event.id());
            return;
        }

        log.info("Received message.published from '{}': {}", event.username(), event.content());

        // sessionId = username gives each user a continuous conversation in ConversationMemory.
        ChatRequest request = new ChatRequest(PERSONALITY, event.content(), event.username());
        ChatResponse response = chatService.chat(request);

        log.info("Bot reply for '{}' (session {}): {}",
                event.username(), response.sessionId(), response.response());

        // Post the reply back as "bot"; it gets persisted + re-published, and the
        // loop-prevention check above stops the bot from answering its own message.
        botReplyClient.postReply(response.response());

        // Mark only after success: a failure leaves the event unmarked so it is retried.
        processedEvents.markProcessed(event.id());
    }
}
