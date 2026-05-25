package org.example.messageservice.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routing-key}")
    private String routingKey;

    public void publish(MessagePublishedEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
