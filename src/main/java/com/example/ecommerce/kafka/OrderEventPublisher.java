package com.example.ecommerce.kafka;

import com.example.ecommerce.domain.Order;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OrderEventPublisher {

    public static final String ORDERS_CREATED_TOPIC = "orders.created";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public OrderEventPublisher(
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = mapToEvent(order);

        CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
            kafkaTemplate.send(
                ORDERS_CREATED_TOPIC,
                order.getOrderNumber(),  // key = order number (deterministic partition)
                event
            );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish order event: {}. Cause: {}",
                    order.getOrderNumber(), ex.getMessage());
                // In production: store in outbox table and retry
            } else {
                log.info("Published order event: {} to partition {} offset {}",
                    order.getOrderNumber(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }

    private OrderCreatedEvent mapToEvent(Order order) {
        var items = order.getItems().stream()
            .map(item -> new OrderCreatedEvent.OrderItemEvent(
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice()
            ))
            .collect(Collectors.toList());

        String shippingCity = order.getShippingAddress() != null
            ? order.getShippingAddress().getCity() : "Unknown";

        return new OrderCreatedEvent(
            order.getId().toString(),
            order.getOrderNumber(),
            order.getCustomer().getEmail(),
            order.getCustomer().getFullName(),
            order.getTotalAmount(),
            items,
            shippingCity
        );
    }
}
