package com.example.ecommerce.service;

import com.example.ecommerce.ai.AnomalyClassificationResult;
import com.example.ecommerce.ai.StubChatModel;
import com.example.ecommerce.domain.OrderAnomalyTriage;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import com.example.ecommerce.repository.OrderAnomalyTriageRepository;
import com.example.ecommerce.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OrderAnomalyTriageService {

    private static final String SYSTEM_PROMPT = """
        You classify ecommerce order checkouts for operations review.
        Choose exactly one label:
        - LIKELY_FRAUD: suspicious customer, amount, or payment pattern
        - GATEWAY_NOISE: payment gateway quirks or transient technical signals
        - CUSTOMER_RETRY: customer likely to retry or fix payment details
        - OPS_REVIEW: default when uncertain or needs human review
        Respond with JSON only: classification, confidence (0-1), rationale (one sentence).
        """;

    private final OrderAnomalyTriageRepository triageRepository;
    private final PaymentRepository paymentRepository;
    private final ChatClient chatClient;
    private final ChatModel chatModel;

    public OrderAnomalyTriageService(OrderAnomalyTriageRepository triageRepository,
                                     PaymentRepository paymentRepository,
                                     ChatClient.Builder chatClientBuilder,
                                     ChatModel chatModel) {
        this.triageRepository = triageRepository;
        this.paymentRepository = paymentRepository;
        this.chatClient = chatClientBuilder.build();
        this.chatModel = chatModel;
    }

    @Transactional
    public void triage(OrderCreatedEvent event) {
        long orderId = Long.parseLong(event.getOrderId());

        if (triageRepository.existsByOrderId(orderId)) {
            log.debug("Order {} already triaged, skipping", event.getOrderNumber());
            return;
        }

        var paymentOpt = paymentRepository.findByOrderId(orderId);
        if (paymentOpt.isEmpty()) {
            log.warn("No payment for order {} (id={}), skipping triage",
                event.getOrderNumber(), orderId);
            return;
        }

        Payment payment = paymentOpt.get();
        AnomalyClassificationResult result = chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user(buildUserPrompt(event, payment))
            .call()
            .entity(AnomalyClassificationResult.class);

        validateResult(result);

        triageRepository.save(new OrderAnomalyTriage(
            orderId,
            event.getOrderNumber(),
            result.classification(),
            result.confidence(),
            result.rationale(),
            resolveModelId()
        ));

        log.info("Order anomaly triage for {}: {}",
            event.getOrderNumber(), result.classification());
    }

    private static void validateResult(AnomalyClassificationResult result) {
        if (result == null || result.classification() == null) {
            throw new IllegalStateException("AI classification returned no classification");
        }
        if (result.rationale() == null || result.rationale().isBlank()) {
            throw new IllegalStateException("AI classification returned empty rationale");
        }
    }

    private static String buildUserPrompt(OrderCreatedEvent event, Payment payment) {
        return """
            Order number: %s
            Customer: %s (%s)
            Total: %s
            Shipping city: %s
            Item count: %d
            Payment status: %s
            Payment amount: %s
            Gateway reference: %s
            Card last4: %s
            """.formatted(
            event.getOrderNumber(),
            event.getCustomerName(),
            event.getCustomerEmail(),
            event.getTotalAmount(),
            event.getShippingCity(),
            event.getItems().size(),
            payment.getStatus(),
            payment.getAmount(),
            payment.getGatewayReference() != null ? payment.getGatewayReference() : "n/a",
            payment.getCardLast4() != null ? payment.getCardLast4() : "n/a"
        );
    }

    private String resolveModelId() {
        if (chatModel instanceof StubChatModel) {
            return StubChatModel.MODEL_ID;
        }
        var options = chatModel.getDefaultOptions();
        if (options != null && options.getModel() != null) {
            return options.getModel();
        }
        return "openai";
    }
}
