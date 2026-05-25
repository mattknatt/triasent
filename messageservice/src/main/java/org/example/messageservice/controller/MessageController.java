package org.example.messageservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.messageservice.model.MessageEntity;
import org.example.messageservice.service.MessageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService service;

    public record CreateMessageRequest(String content) {}

    @PostMapping
    public MessageEntity create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateMessageRequest body) {
        return service.post(jwt.getSubject(), body.content());
    }

    @GetMapping
    public List<MessageEntity> list() {
        return service.all();
    }
}
