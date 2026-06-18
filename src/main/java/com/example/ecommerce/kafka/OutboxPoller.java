package com.example.ecommerce.kafka;

import com.example.ecommerce.domain.OutboxEvent;
import com.example.ecommerce.domain.enums.OutboxStatus;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import com.example.ecommerce.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Component
public class OutboxPoller {

    private final OutboxEventRepository outboxRepository;
    private final OrderEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxPoller(OutboxEventRepository outboxRepository,
                        OrderEventPublisher eventPublisher,
                        ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxRepository.findByStatusOrderByCreatedAtAsc(
            OutboxStatus.PENDING, PageRequest.of(0, 20));

        for (OutboxEvent row : batch) {
            try {
                OrderCreatedEvent event = objectMapper.readValue(
                    row.getPayload(), OrderCreatedEvent.class);
                eventPublisher.publish(event, row.getMessageKey());
                row.markSent();
            } catch (Exception e) {
                log.error("Outbox publish failed for id={}", row.getId(), e);
                row.incrementRetry();
            }
        }
    }
}
