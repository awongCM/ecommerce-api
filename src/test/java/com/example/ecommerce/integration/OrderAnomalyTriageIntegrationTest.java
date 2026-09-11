package com.example.ecommerce.integration;

import com.example.ecommerce.domain.Address;
import com.example.ecommerce.domain.Category;
import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.domain.Order;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.enums.AnomalyClassification;
import com.example.ecommerce.domain.enums.OrderStatus;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.OrderAnomalyTriageRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.service.OrderAnomalyTriageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAnomalyTriageIntegrationTest extends AbstractPostgresIntegrationTest {

    @DynamicPropertySource
    static void enableTriage(DynamicPropertyRegistry registry) {
        registry.add("app.features.order-anomaly-triage", () -> "true");
    }

    @Autowired private OrderAnomalyTriageService triageService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderAnomalyTriageRepository triageRepository;

    @Test
    void triage_shouldPersistStubClassification() {
        Customer customer = customerRepository.save(
            new Customer("Triage", "Tester", "triage@test.com", "hashed"));
        Address address = addressRepository.save(
            new Address(customer, "1 AI St", "Sydney", "NSW", "2000", "AU"));

        Order order = new Order(customer, address, new BigDecimal("49.99"),
            "idem-" + UUID.randomUUID());
        order.transitionTo(OrderStatus.CONFIRMED);
        order = orderRepository.save(order);

        Payment payment = new Payment(order, order.getTotalAmount(), "pay-" + UUID.randomUUID());
        payment.markCaptured("gw-int-test", "1234");
        paymentRepository.save(payment);

        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId().toString(),
            order.getOrderNumber(),
            customer.getEmail(),
            customer.getFullName(),
            order.getTotalAmount(),
            List.of(new OrderCreatedEvent.OrderItemEvent("Test Item", 1, new BigDecimal("49.99"))),
            address.getCity()
        );

        triageService.triage(event);

        var rows = triageRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getOrderId()).isEqualTo(order.getId());
        assertThat(rows.getFirst().getClassification()).isEqualTo(AnomalyClassification.OPS_REVIEW);
        assertThat(rows.getFirst().getModelId()).isEqualTo("stub");
    }
}
