package org.example.messageservice;

import tools.jackson.databind.JsonNode;
import org.example.messageservice.outbox.OutboxEvent;
import org.example.messageservice.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the outbox pattern end to end against a real Postgres + real RabbitMQ. Verifies
 * that a successful POST writes both the message row and the outbox row, that the relay
 * publishes the event with the agreed JSON shape, and that the row gets stamped published.
 */
class OutboxFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanSlate() {
        outboxRepository.deleteAll();
        // Drain any leftover messages from the test queue so this test's assertions only
        // see events it produced.
        while (rabbitTemplate.receive("test.messages.queue", 50) != null) {
            // discard
        }
    }

    @Test
    void postingAMessage_createsOutboxRow_andRelayPublishesToRabbit() throws Exception {
        mockMvc.perform(post("/messages")
                        .with(jwt().jwt(jwtFor("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello outbox\"}"))
                .andExpect(status().isOk());

        // Same transaction wrote the outbox row — it must already exist, unpublished.
        List<OutboxEvent> initial = outboxRepository.findAll();
        assertThat(initial).hasSize(1);
        assertThat(initial.getFirst().getPublishedAt())
                .as("outbox row is created unpublished; the relay stamps it later")
                .isNull();

        // The relay polls every 200 ms; give it generous slack so a slow broker confirm
        // doesn't flake the test. We assert both halves of the success condition: a message
        // arrives in RabbitMQ, AND the row gets stamped published.
        Message delivered = await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> rabbitTemplate.receive("test.messages.queue", 50),
                        msg -> msg != null);

        assertThat(delivered).isNotNull();
        JsonNode body = MAPPER.readTree(delivered.getBody());
        assertThat(body.get("username").asText()).isEqualTo("alice");
        assertThat(body.get("content").asText()).isEqualTo("hello outbox");
        assertThat(body.has("id")).isTrue();
        assertThat(body.has("createdAt")).isTrue();

        await().atMost(Duration.ofSeconds(5))
                .until(() -> outboxRepository.findAll().getFirst().getPublishedAt() != null);
    }
}
