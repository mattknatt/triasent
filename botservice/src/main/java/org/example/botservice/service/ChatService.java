package org.example.botservice.service;

import lombok.RequiredArgsConstructor;
import org.example.botservice.client.LlmClient;
import org.example.botservice.memory.ConversationMemory;
import org.example.botservice.model.ChatRequest;
import org.example.botservice.model.ChatResponse;
import org.example.botservice.model.Message;
import org.example.botservice.personality.PersonalityMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final PersonalityMapper personalityMapper;
    private final ConversationMemory conversationMemory;
    private final LlmClient llmClient;

    public ChatResponse chat(ChatRequest request) {
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();

        String systemPrompt = personalityMapper.getPrompt(request.personality());
        List<Message> history = conversationMemory.getHistory(sessionId);

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.addAll(history);
        messages.add(new Message("user", request.message()));

        String reply = llmClient.sendMessages(messages);
        // Save both messages of the turn atomically so concurrent same-session requests
        // can't interleave and break user/assistant ordering.
        conversationMemory.appendTurn(sessionId,
                new Message("user", request.message()),
                new Message("assistant", reply));
        return new ChatResponse(reply, sessionId);
    }
}
