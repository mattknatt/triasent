package org.example.botservice.messaging;

import org.example.botservice.model.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fetches a user's full conversation transcript from messageservice and maps it into the
 * role/content shape the LLM expects ("assistant" for bot rows, "user" for human rows).
 * Replaces the previous in-process Caffeine cache, so history survives bot restarts and
 * is consistent across replicas — messageservice's database is the single source of truth.
 */
@Component
public class MessageHistoryClient {

    private static final String REGISTRATION_ID = "bot";
    private static final ParameterizedTypeReference<List<MessageItem>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String botUsername;

    public MessageHistoryClient(@Value("${app.messageservice.url}") String baseUrl,
                                @Value("${app.bot.username}") String botUsername,
                                OAuth2AuthorizedClientManager authorizedClientManager) {
        this.restClient = RestClient.create(baseUrl);
        this.botUsername = botUsername;
        this.authorizedClientManager = authorizedClientManager;
    }

    /**
     * Returns the owner's messages in chronological (oldest-first) order, mapped to LLM
     * roles. messageservice sorts desc, so we reverse before returning.
     */
    public List<Message> fetchHistory(String ownerUsername) {
        List<MessageItem> items = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/messages")
                        .queryParam("ownerUsername", ownerUsername)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .retrieve()
                .body(LIST_TYPE);

        if (items == null || items.isEmpty()) return List.of();

        List<Message> history = new ArrayList<>(items.size());
        for (MessageItem item : items) {
            String role = botUsername.equalsIgnoreCase(item.username()) ? "assistant" : "user";
            history.add(new Message(role, item.content()));
        }
        Collections.reverse(history);
        return history;
    }

    private String accessToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal("botservice")
                .build();
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
        if (client == null) {
            throw new IllegalStateException("Could not obtain bot access token");
        }
        return client.getAccessToken().getTokenValue();
    }

    record MessageItem(UUID id, String username, String content, Instant createdAt, String authorRole) {}
}
