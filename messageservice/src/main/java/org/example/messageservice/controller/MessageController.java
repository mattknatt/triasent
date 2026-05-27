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

    @PostMapping
    public MessageEntity create(@AuthenticationPrincipal Jwt jwt,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                @RequestBody CreateMessageRequest body) {
        return service.post(jwt.getSubject(), body.content(), idempotencyKey);
    }

    @GetMapping
    public List<MessageResponse> list() {
        List<MessageEntity> messages = service.all();

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
