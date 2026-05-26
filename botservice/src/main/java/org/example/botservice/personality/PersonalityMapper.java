package org.example.botservice.personality;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PersonalityMapper {

    private static final Map<String, String> PROMPTS = Map.of(
            "helper", "You are a helpful assistant. Answer clearly and concisely, " +
                    "and always make sure the user feels supported.",
            "pirate", "You are a pirate. Respond in pirate slang.",
            "coder", "You are an expert software engineer. Give precise and technical answers.");

    public String getPrompt(String personality) {
        String prompt = PROMPTS.get(personality);
        if (prompt == null) {
            throw new IllegalArgumentException("Unknown personality: " + personality);
        }
        return prompt;
    }
}

