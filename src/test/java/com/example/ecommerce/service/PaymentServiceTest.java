package com.example.ecommerce.service;

import com.example.ecommerce.config.AppProperties;
import com.example.ecommerce.domain.Address;
import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.domain.Order;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.payment.PaymentCaptureResult;
import com.example.ecommerce.payment.PaymentGatewayClient;
import com.example.ecommerce.payment.PaymentGatewayException;
import com.example.ecommerce.payment.PaymentOutcome;
import com.example.ecommerce.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGatewayClient paymentGatewayClient;
    @Mock private AppProperties appProperties;
    @Mock private AppProperties.PaymentGateway paymentGatewayProps;

    private PaymentService paymentService;
    private Order order;

    @BeforeEach
    void setUp() {
        when(appProperties.getPaymentGateway()).thenReturn(paymentGatewayProps);
        when(paymentGatewayProps.getCurrency()).thenReturn("usd");
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        paymentService = new PaymentService(
            paymentRepository, paymentGatewayClient, appProperties);

        Customer customer = new Customer("John", "Doe", "john@example.com", "hash");
        Address address = new Address(customer, "1 St", "Sydney", "NSW", "2000", "AU");
        order = new Order(customer, address, new BigDecimal("10.00"), "idem-1");
    }

    @Test
    void processPayment_shouldReturnCaptured_whenGatewaySucceeds() {
        when(paymentGatewayClient.capture(eq("tok_ok"), any(), eq("usd"), eq("idem-1")))
            .thenReturn(new PaymentCaptureResult("pi_123", "4242", "succeeded"));

        PaymentOutcome outcome = paymentService.processPayment(order, "tok_ok");

        assertThat(outcome).isInstanceOf(PaymentOutcome.Captured.class);
        PaymentOutcome.Captured captured = (PaymentOutcome.Captured) outcome;
        assertThat(captured.gatewayReference()).isEqualTo("pi_123");
        assertThat(captured.cardLast4()).isEqualTo("4242");
    }

    @Test
    void processPayment_shouldReturnFailed_whenGatewayDeclinesNonRetryable() {
        when(paymentGatewayClient.capture(eq("tok_declined"), any(), eq("usd"), eq("idem-1")))
            .thenThrow(new PaymentGatewayException("card declined", false));

        PaymentOutcome outcome = paymentService.processPayment(order, "tok_declined");

        assertThat(outcome).isInstanceOf(PaymentOutcome.Failed.class);
        assertThat(((PaymentOutcome.Failed) outcome).reason()).contains("card declined");
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(any(Payment.class));
    }

    @Test
    void processPayment_shouldRethrow_whenGatewayExceptionIsRetryable() {
        when(paymentGatewayClient.capture(eq("tok_outage"), any(), eq("usd"), eq("idem-1")))
            .thenThrow(new PaymentGatewayException("connection reset", true));

        assertThatThrownBy(() -> paymentService.processPayment(order, "tok_outage"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("connection reset")
            .satisfies(ex -> assertThat(((PaymentGatewayException) ex).isRetryable()).isTrue());
    }
}
