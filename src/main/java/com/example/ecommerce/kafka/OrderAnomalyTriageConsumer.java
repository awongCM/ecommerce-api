package com.example.ecommerce.kafka;

import com.example.ecommerce.config.AppProperties;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import com.example.ecommerce.service.OrderAnomalyTriageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderAnomalyTriageConsumer {

    static final String CONSUMER_GROUP = "order-anomaly-triage";

    private final OrderAnomalyTriageService triageService;
    private final AppProperties appProperties;

    public OrderAnomalyTriageConsumer(OrderAnomalyTriageService triageService,
                                        AppProperties appProperties) {
        this.triageService = triageService;
        this.appProperties = appProperties;
    }

    @KafkaListener(
        topics = OrderEventPublisher.ORDERS_CREATED_TOPIC,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(
            ConsumerRecord<String, OrderCreatedEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        if (!appProperties.getFeatures().isOrderAnomalyTriage()) {
            log.debug("Order anomaly triage disabled, skipping offset {}", offset);
            return;
        }

        OrderCreatedEvent event = record.value();
        MDC.put("orderNumber", event.getOrderNumber());

        try {
            log.info("Triage consumer processing order {} from partition {} offset {}",
                event.getOrderNumber(), partition, offset);
            triageService.triage(event);
        } catch (Exception e) {
            log.error("Failed to triage order {}", event.getOrderNumber(), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }
}
