package org.example.botservice.client;

import lombok.RequiredArgsConstructor;
import org.example.botservice.config.LlmProperties;
import org.example.botservice.exception.LlmClientException;
import org.example.botservice.exception.LlmUnavailableException;
import org.example.botservice.model.Message;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LlmClient {

    private final RestClient restClient;
    private final LlmProperties llmProperties;

    @Retryable(
            includes = LlmUnavailableException.class,
            maxRetries = 3,
            delay = 1000,
            multiplier = 2,
            jitter = 200,
            maxDelay = 5000
    )
    public String sendMessages(List<Message> messages) {
        LlmRequest request = new LlmRequest(llmProperties.model(), messages);
        LlmResponse response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 429 || status.is5xxServerError(),
                        (req, res) -> { throw new LlmUnavailableException("LLM unavailable"); })
                .onStatus(status -> status.is4xxClientError() && status.value() != 429,
                        (req, res) -> { int code = res.getStatusCode().value(); throw new LlmClientException(code, "LLM rejected request with status " + code); })
                .body(LlmResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("LLM returned no choices");
        }
        return response.choices().get(0).message().content();
    }
}
