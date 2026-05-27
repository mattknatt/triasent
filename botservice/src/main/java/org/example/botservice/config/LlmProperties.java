package org.example.botservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.api")
public record LlmProperties(
        String url,
        String key,
        String model
) {
    @Override
    public String toString() {
        return "LlmProperties{url='" + url + "', key='<redacted>', model='" + model + "'}";
    }
}
