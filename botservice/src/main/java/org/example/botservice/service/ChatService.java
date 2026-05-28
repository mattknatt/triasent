package org.example.botservice.service;

import lombok.RequiredArgsConstructor;
import org.example.botservice.client.LlmClient;
import org.example.botservice.messaging.MessageHistoryClient;
import org.example.botservice.model.ChatRequest;
import org.example.botservice.model.ChatResponse;
import org.example.botservice.model.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM_PROMPT =
            "You are a healthcare support assistant inside a medical and wellness application." +
            "Your role is to provide clear, calm, evidence-based, and safe health information to users." +
            "You are not a replacement for a licensed healthcare professional, and you must never claim to diagnose conditions or prescribe treatment.";

    private final MessageHistoryClient historyClient;
    private final LlmClient llmClient;

    public ChatResponse chat(ChatRequest request) {
        // sessionId now carries the user's UUID (set by MessageEventListener from the event).
        // The fallback random UUID keeps the response shape consistent if some other caller
        // ever invokes ChatService without a session — history will simply be empty.
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();
        UUID ownerUserId = UUID.fromString(sessionId);

        // History from messageservice already includes the user message we're responding to
        // (messageservice persists the row before publishing the event), so we don't append
        // request.message() again. The empty-history branch is the fallback for the very
        // first turn or if the read fails: we still want the LLM to see something.
        List<Message> history = historyClient.fetchHistory(ownerUserId);

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", SYSTEM_PROMPT));
        if (history.isEmpty()) {
            messages.add(new Message("user", request.message()));
        } else {
            messages.addAll(history);
        }

        String reply = llmClient.sendMessages(messages);
        return new ChatResponse(reply, sessionId);
    }
}
