package org.example.botservice.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Posts the bot's reply to messageservice (POST /messages) with a client-credentials
 * bearer token. The bot's JWT subject is the reserved bot UUID, so the message is
 * authored by the bot but attributed to the originating user's conversation via the
 * X-Conversation-Owner header.
 */
@Component
public class BotReplyClient {

    private static final String REGISTRATION_ID = "bot";

    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public BotReplyClient(@Value("${app.messageservice.url}") String baseUrl,
                          OAuth2AuthorizedClientManager authorizedClientManager) {
        this.restClient = RestClient.create(baseUrl);
        this.authorizedClientManager = authorizedClientManager;
    }

    public void postReply(String content, UUID conversationOwner, String idempotencyKey) {
        restClient.post()
                .uri("/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Conversation-Owner", conversationOwner.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateMessageRequest(content))
                .retrieve()
                .toBodilessEntity();
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

    record CreateMessageRequest(String content) {}
}
