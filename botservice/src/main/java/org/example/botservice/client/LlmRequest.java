package org.example.botservice.client;

import org.example.botservice.model.Message;

import java.util.List;

public record LlmRequest(String model, List<Message> messages) {}
