package org.example.botservice.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.botservice.model.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ConversationMemory {

    private static final int MAX_HISTORY = 20;

    private final Cache<String, List<Message>> cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    public List<Message> getHistory(String sessionId) {
        if (sessionId == null) return List.of();
        List<Message> history = cache.getIfPresent(sessionId);
        return history != null ? new ArrayList<>(history) : List.of();
    }

    public void append(String sessionId, Message message) {
        appendTurn(sessionId, message);
    }

    /**
     * Appends all messages of a turn in one atomic update, so concurrent requests for the
     * same session can't interleave and break user/assistant ordering.
     */
    public void appendTurn(String sessionId, Message... messages) {
        if (sessionId == null || messages.length == 0) return;
        cache.asMap().compute(sessionId, (k, existing) -> {
            List<Message> history = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
            for (Message message : messages) {
                history.add(message);
            }
            if (history.size() > MAX_HISTORY) {
                return new ArrayList<>(history.subList(history.size() - MAX_HISTORY, history.size()));
            }
            return history;
        });
    }
}