package org.example.messageservice;

import org.example.messageservice.client.UserServiceClient;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Base for integration tests: boots Postgres + RabbitMQ via Testcontainers (wired in by
 * {@link ServiceConnection}) and replaces the JWT decoder so MockMvc's {@code jwt()}
 * post-processor can drive authz without a live authservice. UserServiceClient is mocked
 * because the GET-enrichment path would otherwise dial userservice over gRPC.
 *
 * <p>The Rabbit speed-up: outbox poll interval is dropped to 200 ms so the publish path
 * is observable inside an Awaitility wait; broker confirms still need real I/O so we keep
 * the timeout generous.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.outbox.poll-interval-ms=200",
        // Bypassed by the test JwtDecoder bean below, but must be set so the resource-server
        // auto-config considers itself configured at bean-definition time.
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/unused"
})
@Import(AbstractIntegrationTest.TestBeans.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3-management");

    // GET /messages enriches author roles via gRPC -> userservice. Tests don't run a
    // userservice, so we hand back empty profile maps and the controller returns null roles.
    @MockitoBean
    protected UserServiceClient userServiceClient;

    @TestConfiguration
    static class TestBeans {

        /**
         * Replaces the auto-configured Nimbus decoder so startup doesn't try to fetch JWKS
         * from a real issuer. MockMvc's {@code jwt()} post-processor populates the security
         * context directly, so this decoder is never invoked at request time.
         */
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException(
                        "Test JwtDecoder: use SecurityMockMvcRequestPostProcessors.jwt() instead");
            };
        }

        /**
         * Without a bound queue, the outbox relay's mandatory publishes come back unroutable
         * and get parked. This queue gives the test something to observe and lets the broker
         * route the event so {@code publishedAt} gets stamped.
         */
        @Bean
        Queue testMessagesQueue() {
            return new Queue("test.messages.queue", false);
        }

        @Bean
        Binding testMessagesBinding(Queue testMessagesQueue, TopicExchange messagesExchange) {
            return BindingBuilder.bind(testMessagesQueue).to(messagesExchange).with("message.published");
        }
    }

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    /** Builds the Jwt the {@code jwt(...).jwt(j)} post-processor will pin into the test request. */
    protected static Jwt jwtFor(String subject) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "none");
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        return new Jwt("token-" + subject, Instant.now(), Instant.now().plusSeconds(300), headers, claims);
    }

    protected void mockUserProfilesEmpty(Collection<String> usernames) {
        org.mockito.Mockito.when(userServiceClient.profilesByUsername(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of());
    }
}
