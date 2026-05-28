package org.example.messageservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.grpc.UserProfile;
import org.example.messageservice.client.UserServiceClient;
import org.example.messageservice.dto.MessageResponse;
import org.example.messageservice.model.MessageEntity;
import org.example.messageservice.service.MessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private static final String BOT_DISPLAY_NAME = "bot";

    private final MessageService service;
    private final UserServiceClient userServiceClient;

    @Value("${app.bot.user-id}")
    private UUID botUserId;

    public record CreateMessageRequest(String content) {}

    @PostMapping
    public MessageEntity create(@AuthenticationPrincipal Jwt jwt,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                @RequestHeader(value = "X-Conversation-Owner", required = false) UUID conversationOwner,
                                @RequestBody CreateMessageRequest body) {
        UUID author = UUID.fromString(jwt.getSubject());
        // Only the bot may attribute a message to another user's conversation. Human posts
        // always own their own thread — the header is ignored for them, which prevents one
        // user from planting messages in another's chat.
        UUID owner = botUserId.equals(author) && conversationOwner != null
                ? conversationOwner
                : author;
        return service.post(author, owner, body.content(), idempotencyKey);
    }

    @GetMapping
    public List<MessageResponse> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(value = "ownerUserId", required = false) UUID ownerUserIdParam) {
        UUID caller = UUID.fromString(jwt.getSubject());
        // Mirror of the POST rule: only the bot may read someone else's thread (it needs
        // the user's full transcript to build LLM context). Human users are pinned to
        // their own conversation regardless of what the query param says.
        UUID owner = botUserId.equals(caller) && ownerUserIdParam != null
                ? ownerUserIdParam
                : caller;
        List<MessageEntity> messages = service.forOwner(owner);

        // Enrich each unique non-bot author with display info from userservice. The bot
        // doesn't have a userservice row; we substitute a fixed display name and skip the
        // gRPC roundtrip for it.
        Set<UUID> authorsToLookup = messages.stream()
                .map(MessageEntity::getUserId)
                .filter(id -> !botUserId.equals(id))
                .collect(Collectors.toSet());
        Map<UUID, UserProfile> profiles = userServiceClient.profilesById(authorsToLookup);

        return messages.stream()
                .map(m -> {
                    UUID authorId = m.getUserId();
                    if (botUserId.equals(authorId)) {
                        return new MessageResponse(m.getId(), authorId, BOT_DISPLAY_NAME, m.getContent(), m.getCreatedAt(), null);
                    }
                    UserProfile profile = profiles.get(authorId);
                    String username = profile != null ? profile.getUsername() : authorId.toString();
                    String role = profile != null ? profile.getRole() : null;
                    return new MessageResponse(m.getId(), authorId, username, m.getContent(), m.getCreatedAt(), role);
                })
                .toList();
    }
}
