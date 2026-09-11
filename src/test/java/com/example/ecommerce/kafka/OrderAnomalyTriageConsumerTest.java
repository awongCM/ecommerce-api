package com.example.ecommerce.kafka;

import com.example.ecommerce.config.AppProperties;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import com.example.ecommerce.service.OrderAnomalyTriageService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderAnomalyTriageConsumerTest {

    @Mock private OrderAnomalyTriageService triageService;
    @Mock private AppProperties appProperties;

    @InjectMocks
    private OrderAnomalyTriageConsumer consumer;

    @Test
    void onOrderCreated_whenFlagOff_shouldSkipTriage() {
        AppProperties.Features features = new AppProperties.Features();
        features.setOrderAnomalyTriage(false);
        org.mockito.Mockito.when(appProperties.getFeatures()).thenReturn(features);

        OrderCreatedEvent event = new OrderCreatedEvent(
            "1", "ORD-1", "a@test.com", "A Test",
            BigDecimal.TEN, List.of(), "Sydney");
        ConsumerRecord<String, OrderCreatedEvent> record =
            new ConsumerRecord<>("orders.created", 0, 0L, "ORD-1", event);

        consumer.onOrderCreated(record, 0, 0L);

        verify(triageService, never()).triage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onOrderCreated_whenFlagOn_shouldDelegateToService() {
        AppProperties.Features features = new AppProperties.Features();
        features.setOrderAnomalyTriage(true);
        org.mockito.Mockito.when(appProperties.getFeatures()).thenReturn(features);

        OrderCreatedEvent event = new OrderCreatedEvent(
            "1", "ORD-1", "a@test.com", "A Test",
            BigDecimal.TEN, List.of(), "Sydney");
        ConsumerRecord<String, OrderCreatedEvent> record =
            new ConsumerRecord<>("orders.created", 0, 0L, "ORD-1", event);

        consumer.onOrderCreated(record, 0, 0L);

        verify(triageService).triage(event);
    }
}
