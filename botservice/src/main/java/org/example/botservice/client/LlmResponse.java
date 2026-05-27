package org.example.botservice.client;

import java.util.List;

public record LlmResponse(List<CompletionChoice> choices) {}
