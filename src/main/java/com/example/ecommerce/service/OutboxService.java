package com.example.ecommerce.service;

import com.example.ecommerce.domain.Order;
import com.example.ecommerce.domain.OutboxEvent;
import com.example.ecommerce.kafka.OrderCreatedEventMapper;
import com.example.ecommerce.kafka.OrderEventPublisher;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import com.example.ecommerce.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private static final String AGGREGATE_TYPE = "ORDER";
    private static final String EVENT_TYPE = "ORDER_CREATED";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueueOrderCreated(Order order) {
        OrderCreatedEvent event = OrderCreatedEventMapper.fromOrder(order);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order event", e);
        }

        OutboxEvent row = new OutboxEvent(
            AGGREGATE_TYPE,
            order.getId().toString(),
            EVENT_TYPE,
            OrderEventPublisher.ORDERS_CREATED_TOPIC,
            order.getOrderNumber(),
            payload
        );
        outboxRepository.save(row);
    }
}
