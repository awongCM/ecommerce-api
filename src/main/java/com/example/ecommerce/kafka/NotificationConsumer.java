package com.example.ecommerce.kafka;

import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationConsumer {

    @KafkaListener(
        topics = OrderEventPublisher.ORDERS_CREATED_TOPIC,
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(
            ConsumerRecord<String, OrderCreatedEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        OrderCreatedEvent event = record.value();

        // Add correlation data to log context
        MDC.put("orderNumber", event.getOrderNumber());
        MDC.put("customerEmail", event.getCustomerEmail());

        try {
            log.info("Processing order notification: {} from partition {} offset {}",
                event.getOrderNumber(), partition, offset);

            // In production: inject an EmailService and send via SES/SendGrid
            sendOrderConfirmationEmail(event);

            log.info("Order confirmation sent to: {}", event.getCustomerEmail());
        } catch (Exception e) {
            // Log and allow Kafka to retry based on retry configuration
            log.error("Failed to send notification for order: {}",
                event.getOrderNumber(), e);
            throw e; // re-throw so Kafka retries
        } finally {
            MDC.clear();
        }
    }

    private void sendOrderConfirmationEmail(OrderCreatedEvent event) {
        // Mock implementation — in real life, call email provider API
        log.info("""
            ---- ORDER CONFIRMATION EMAIL ----
            To: {}
            Subject: Order {} confirmed!
            Total: ${}
            Items: {}
            ----------------------------------
            """,
            event.getCustomerEmail(),
            event.getOrderNumber(),
            event.getTotalAmount(),
            event.getItems().size()
        );
    }
}
