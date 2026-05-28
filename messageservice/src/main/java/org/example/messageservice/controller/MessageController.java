package org.example.messageservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.grpc.UserProfile;
import org.example.messageservice.client.UserServiceClient;
import org.example.messageservice.dto.MessageResponse;
import org.example.messageservice.model.MessageEntity;
import org.example.messageservice.service.MessageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService service;
    private final UserServiceClient userServiceClient;

    public record CreateMessageRequest(String content) {}

    private static final String BOT_SUBJECT = "bot";

    @PostMapping
    public MessageEntity create(@AuthenticationPrincipal Jwt jwt,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                @RequestHeader(value = "X-Conversation-Owner", required = false) String conversationOwner,
                                @RequestBody CreateMessageRequest body) {
        String author = jwt.getSubject();
        // Only the bot (client_credentials token, subject = "bot") may attribute a message
        // to another user's conversation. Human posts always own their own thread, so the
        // header is ignored for them — prevents one user from planting messages in another.
        String owner = BOT_SUBJECT.equals(author) && conversationOwner != null
                ? conversationOwner
                : author;
        return service.post(author, owner, body.content(), idempotencyKey);
    }

    @GetMapping
    public List<MessageResponse> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(value = "ownerUsername", required = false) String ownerUsernameParam) {
        String caller = jwt.getSubject();
        // Mirror of the POST rule: only the bot may read someone else's thread (it needs
        // the user's full transcript to build LLM context). Human users are pinned to
        // their own conversation regardless of what the query param says.
        String owner = BOT_SUBJECT.equals(caller) && ownerUsernameParam != null
                ? ownerUsernameParam
                : caller;
        List<MessageEntity> messages = service.forOwner(owner);

        // Enrich each message with its author's role, fetched from userservice over gRPC.
        Set<String> authors = messages.stream()
                .map(MessageEntity::getUsername)
                .collect(Collectors.toSet());
        Map<String, UserProfile> profiles = userServiceClient.profilesByUsername(authors);

        return messages.stream()
                .map(m -> new MessageResponse(
                        m.getId(),
                        m.getUsername(),
                        m.getContent(),
                        m.getCreatedAt(),
                        profiles.containsKey(m.getUsername()) ? profiles.get(m.getUsername()).getRole() : null))
                .toList();
    }
}
