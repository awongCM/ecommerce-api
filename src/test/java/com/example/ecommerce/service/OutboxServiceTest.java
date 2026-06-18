package com.example.ecommerce.service;

import com.example.ecommerce.domain.Address;
import com.example.ecommerce.domain.Category;
import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.domain.Order;
import com.example.ecommerce.domain.OrderItem;
import com.example.ecommerce.domain.OutboxEvent;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.domain.enums.OutboxStatus;
import com.example.ecommerce.kafka.OrderEventPublisher;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import com.example.ecommerce.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock private OutboxEventRepository outboxRepository;

    private ObjectMapper objectMapper;
    private OutboxService outboxService;
    private Order order;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        outboxService = new OutboxService(outboxRepository, objectMapper);

        Customer customer = new Customer("Alice", "Smith",
            "alice@example.com", "hashed");
        Address address = new Address(customer,
            "1 Test St", "Sydney", "NSW", "2000", "AU");
        Product product = new Product("Laptop", "Gaming laptop",
            new BigDecimal("999.99"), 10, new Category("Electronics"), "LAP-001");

        order = new Order(customer, address,
            new BigDecimal("1999.98"), "idem-key-123");
        ReflectionTestUtils.setField(order, "id", 42L);
        ReflectionTestUtils.setField(order, "orderNumber", "ORD-TEST123");
        order.getItems().add(new OrderItem(order, product, 2));
    }

    @Test
    void enqueueOrderCreated_shouldSavePendingOutboxRowWithSerializedPayload() throws Exception {
        when(outboxRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        outboxService.enqueueOrderCreated(order);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("ORDER");
        assertThat(saved.getAggregateId()).isEqualTo("42");
        assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getTopic()).isEqualTo(OrderEventPublisher.ORDERS_CREATED_TOPIC);
        assertThat(saved.getMessageKey()).isEqualTo("ORD-TEST123");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();

        OrderCreatedEvent event = objectMapper.readValue(
            saved.getPayload(), OrderCreatedEvent.class);
        assertThat(event.getOrderId()).isEqualTo("42");
        assertThat(event.getOrderNumber()).isEqualTo("ORD-TEST123");
        assertThat(event.getCustomerEmail()).isEqualTo("alice@example.com");
        assertThat(event.getCustomerName()).isEqualTo("Alice Smith");
        assertThat(event.getTotalAmount()).isEqualByComparingTo("1999.98");
        assertThat(event.getShippingCity()).isEqualTo("Sydney");
        assertThat(event.getItems()).hasSize(1);
        assertThat(event.getItems().get(0).getProductName()).isEqualTo("Laptop");
        assertThat(event.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(event.getItems().get(0).getUnitPrice())
            .isEqualByComparingTo("999.99");
    }

    @Test
    void enqueueOrderCreated_shouldThrow_whenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
            .thenThrow(new JsonProcessingException("boom") {});
        OutboxService service = new OutboxService(outboxRepository, failingMapper);

        assertThatThrownBy(() -> service.enqueueOrderCreated(order))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to serialize order event")
            .hasCauseInstanceOf(JsonProcessingException.class);

        verify(outboxRepository, never()).save(any());
    }
}
