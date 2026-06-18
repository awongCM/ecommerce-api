package com.example.ecommerce.kafka;

import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class OrderEventPublisher {

    public static final String ORDERS_CREATED_TOPIC = "orders.created";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderEventPublisher(
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(OrderCreatedEvent event, String messageKey) {
        CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
            kafkaTemplate.send(ORDERS_CREATED_TOPIC, messageKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish order event: {}. Cause: {}",
                    messageKey, ex.getMessage());
            } else {
                log.info("Published order event: {} to partition {} offset {}",
                    messageKey,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}
