package com.example.ecommerce.service;

import com.example.ecommerce.config.AppProperties;
import com.example.ecommerce.domain.Order;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.payment.PaymentCaptureResult;
import com.example.ecommerce.payment.PaymentGatewayClient;
import com.example.ecommerce.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final AppProperties appProperties;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentGatewayClient paymentGatewayClient,
            AppProperties appProperties) {
        this.paymentRepository = paymentRepository;
        this.paymentGatewayClient = paymentGatewayClient;
        this.appProperties = appProperties;
    }

    /**
     * Processes payment with:
     * - Circuit breaker (fail fast if gateway is down)
     * - Retry (3 attempts with backoff)
     * - Runs in the caller transaction so FK constraints can
     *   safely reference the just-created order row
     */
    @Transactional
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentGateway")
    public void processPayment(Order order, String paymentToken) {
        Payment payment = new Payment(
            order, order.getTotalAmount(), order.getIdempotencyKey());
        payment = paymentRepository.save(payment);

        try {
            PaymentCaptureResult result = paymentGatewayClient.capture(
                paymentToken,
                order.getTotalAmount(),
                appProperties.getPaymentGateway().getCurrency(),
                order.getIdempotencyKey());

            payment.markCaptured(result.gatewayReference(), result.cardLast4());
            paymentRepository.save(payment);
            order.setPayment(payment);

            log.info("Payment captured for order: {}, ref: {}",
                order.getOrderNumber(), result.gatewayReference());

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
}
