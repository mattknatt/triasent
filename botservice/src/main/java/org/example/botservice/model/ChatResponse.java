package org.example.botservice.model;

public record ChatResponse(
        String response,
        String sessionId
) {
}