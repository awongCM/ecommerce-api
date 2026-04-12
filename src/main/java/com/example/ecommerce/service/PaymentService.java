package com.example.ecommerce.service;

import com.example.ecommerce.domain.Order;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log =
        LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    // In a real system, inject a PaymentGatewayClient here

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Processes payment with:
     * - Circuit breaker (fail fast if gateway is down)
     * - Retry (3 attempts with backoff)
     * - Runs in its OWN transaction (REQUIRES_NEW)
     *   so payment record is saved even if caller rolls back
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CircuitBreaker(name = "paymentGateway",
                    fallbackMethod = "paymentFallback")
    @Retry(name = "paymentGateway")
    public void processPayment(Order order, String paymentToken) {
        Payment payment = new Payment(
            order,
            order.getTotalAmount(),
            order.getIdempotencyKey()
        );
        payment = paymentRepository.save(payment);

        try {
            // Simulate calling external payment gateway
            String gatewayRef = callPaymentGateway(
                paymentToken, order.getTotalAmount(), order.getIdempotencyKey());

            // Store only last 4 digits of card — NEVER full number
            payment.markCaptured(gatewayRef, "4242");
            paymentRepository.save(payment);
            order.setPayment(payment);

            log.info("Payment captured for order: {}, ref: {}",
                order.getOrderNumber(), gatewayRef);

        } catch (Exception e) {
            payment.markFailed();
            paymentRepository.save(payment);
            throw e;
        }
    }

    // Called when circuit is OPEN or all retries exhausted
    public void paymentFallback(Order order, String token, Throwable t) {
        log.error("Payment gateway unavailable for order: {}. Cause: {}",
            order.getOrderNumber(), t.getMessage());
        throw new RuntimeException(
            "Payment gateway temporarily unavailable. Please try again later.");
    }

    private String callPaymentGateway(String token, java.math.BigDecimal amount,
                                       String idempotencyKey) {
        // Mock implementation — replace with real HTTP call
        // e.g., Stripe, Adyen, Braintree SDK
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Invalid payment token");
        }
        return "GW-" + System.currentTimeMillis();
    }
}
