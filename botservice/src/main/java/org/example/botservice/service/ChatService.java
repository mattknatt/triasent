package org.example.botservice.service;

import lombok.RequiredArgsConstructor;
import org.example.botservice.client.LlmClient;
import org.example.botservice.messaging.MessageHistoryClient;
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
    private final MessageHistoryClient historyClient;
    private final LlmClient llmClient;

    public ChatResponse chat(ChatRequest request) {
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();

        String systemPrompt = personalityMapper.getPrompt(request.personality());
        // History from messageservice already includes the user message we're responding to
        // (messageservice persists the row before publishing the event), so we don't append
        // request.message() again. The empty-history branch is the fallback for the very
        // first turn or if the read fails: we still want the LLM to see something.
        List<Message> history = historyClient.fetchHistory(sessionId);

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        if (history.isEmpty()) {
            messages.add(new Message("user", request.message()));
        } else {
            messages.addAll(history);
        }

        String reply = llmClient.sendMessages(messages);
        return new ChatResponse(reply, sessionId);
    }
}
