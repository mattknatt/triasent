package org.example.botservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumer-side topology for message.published events.
 *
 * <p>A durable queue is bound to the (existing, producer-owned) {@code messages.exchange}.
 * Messages the listener rejects are dead-lettered to a private DLX/DLQ instead of being
 * dropped or requeued forever — this is the consumer-side dead-letter queue.
 */
@Configuration
public class RabbitConfig {

    /** Routing key used between the DLX and the DLQ. */
    private static final String DLQ_ROUTING_KEY = "bot.dlq";

    @Value("${app.messaging.exchange}")
    private String exchangeName;

    @Value("${app.messaging.routing-key}")
    private String routingKey;

    @Value("${app.messaging.queue}")
    private String queueName;

    @Value("${app.messaging.dlx}")
    private String dlxName;

    @Value("${app.messaging.dlq}")
    private String dlqName;

    // The exchange messageservice publishes to. Declared with the same args (durable topic)
    // so startup is idempotent whether or not the producer got here first.
    @Bean
    TopicExchange messagesExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue botQueue() {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding botQueueBinding() {
        return BindingBuilder.bind(botQueue()).to(messagesExchange()).with(routingKey);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxName, true, false);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ_ROUTING_KEY);
    }

    // Lets @RabbitListener deserialize the JSON body into the event record by field names.
    // Spring Boot wires this single MessageConverter bean into the listener container.
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
