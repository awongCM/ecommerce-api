package com.example.ecommerce.payment.stripe;

import com.example.ecommerce.config.AppProperties;
import com.example.ecommerce.domain.Order;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.ProcessedWebhookEvent;
import com.example.ecommerce.domain.enums.OrderStatus;
import com.example.ecommerce.domain.enums.PaymentStatus;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.ProcessedWebhookEventRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.payment-gateway.provider", havingValue = "stripe")
public class StripeWebhookService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final String webhookSecret;

    public StripeWebhookService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            AppProperties appProperties) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.webhookSecret = appProperties.getStripe().getWebhookSecret();
    }

    @Transactional
    public void handle(String payload, String sigHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET is not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature", e);
        }

        if (processedWebhookEventRepository.existsById(event.getId())) {
            log.debug("Ignoring duplicate Stripe webhook event: {}", event.getId());
            return;
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> onPaymentSucceeded(event);
            case "payment_intent.payment_failed" -> onPaymentFailed(event);
            default -> log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }

        processedWebhookEventRepository.save(new ProcessedWebhookEvent(event.getId()));
    }

    private void onPaymentSucceeded(Event event) {
        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
            .getObject()
            .orElseThrow(() -> new IllegalStateException(
                "Could not deserialize payment_intent from webhook event"));

        Payment payment = findPayment(intent).orElse(null);
        if (payment == null) {
            log.warn("No payment found for Stripe PaymentIntent {}", intent.getId());
            return;
        }
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return;
        }

        payment.markCaptured(intent.getId(), StripePaymentSupport.extractLast4(intent));
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PENDING) {
            order.transitionTo(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        }
    }

    private void onPaymentFailed(Event event) {
        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
            .getObject()
            .orElseThrow(() -> new IllegalStateException(
                "Could not deserialize payment_intent from webhook event"));

        findPayment(intent).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.CAPTURED) {
                payment.markFailed();
                paymentRepository.save(payment);
            }
        });
    }

    private java.util.Optional<Payment> findPayment(PaymentIntent intent) {
        String idempotencyKey = intent.getMetadata().get("idempotency_key");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .or(() -> paymentRepository.findByGatewayReference(intent.getId()));
        }
        return paymentRepository.findByGatewayReference(intent.getId());
    }
}
