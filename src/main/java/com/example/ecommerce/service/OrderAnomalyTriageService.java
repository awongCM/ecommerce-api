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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

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
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    @Autowired
    public OrderAnomalyTriageService(OrderAnomalyTriageRepository triageRepository,
                                     PaymentRepository paymentRepository,
                                     ChatClient.Builder chatClientBuilder,
                                     ChatModel chatModel,
                                     PlatformTransactionManager transactionManager) {
        this.triageRepository = triageRepository;
        this.paymentRepository = paymentRepository;
        this.chatClient = chatClientBuilder.build();
        this.chatModel = chatModel;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    /** Test-only factory when custom transaction templates are needed. */
    static OrderAnomalyTriageService forTests(OrderAnomalyTriageRepository triageRepository,
                                              PaymentRepository paymentRepository,
                                              ChatClient.Builder chatClientBuilder,
                                              ChatModel chatModel,
                                              TransactionTemplate readTx,
                                              TransactionTemplate writeTx) {
        return new OrderAnomalyTriageService(
            triageRepository, paymentRepository, chatClientBuilder, chatModel, readTx, writeTx);
    }

    private OrderAnomalyTriageService(OrderAnomalyTriageRepository triageRepository,
                                      PaymentRepository paymentRepository,
                                      ChatClient.Builder chatClientBuilder,
                                      ChatModel chatModel,
                                      TransactionTemplate readTx,
                                      TransactionTemplate writeTx) {
        this.triageRepository = triageRepository;
        this.paymentRepository = paymentRepository;
        this.chatClient = chatClientBuilder.build();
        this.chatModel = chatModel;
        this.readTx = readTx;
        this.writeTx = writeTx;
    }

    public void triage(OrderCreatedEvent event) {
        Long orderId = parseOrderId(event.getOrderId(), event.getOrderNumber());
        if (orderId == null) {
            return;
        }

        PrepareResult prepared = readTx.execute(status -> prepare(orderId, event.getOrderNumber()));
        if (!(prepared instanceof PrepareResult.Ready(Payment payment))) {
            return;
        }

        // LLM call intentionally outside any DB transaction
        AnomalyClassificationResult result = classify(event, payment);
        validateResult(result);

        writeTx.executeWithoutResult(status ->
            persist(orderId, event.getOrderNumber(), result));

        log.info("Order anomaly triage for {}: {}",
            event.getOrderNumber(), result.classification());
    }

    private PrepareResult prepare(long orderId, String orderNumber) {
        if (triageRepository.existsByOrderId(orderId)) {
            log.debug("Order {} already triaged, skipping", orderNumber);
            return new PrepareResult.AlreadyTriaged();
        }
        var paymentOpt = paymentRepository.findByOrderId(orderId);
        if (paymentOpt.isEmpty()) {
            log.warn("No payment for order {} (id={}), skipping triage", orderNumber, orderId);
            return new PrepareResult.NoPayment();
        }
        return new PrepareResult.Ready(paymentOpt.get());
    }

    private AnomalyClassificationResult classify(OrderCreatedEvent event, Payment payment) {
        boolean externalProvider = !(chatModel instanceof StubChatModel);
        return chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user(buildUserPrompt(event, payment, externalProvider))
            .call()
            .entity(AnomalyClassificationResult.class);
    }

    private void persist(long orderId, String orderNumber, AnomalyClassificationResult result) {
        try {
            triageRepository.save(new OrderAnomalyTriage(
                orderId,
                orderNumber,
                result.classification(),
                result.confidence(),
                result.rationale(),
                resolveModelId()
            ));
        } catch (DataIntegrityViolationException ex) {
            log.debug("Order {} already triaged (concurrent insert), skipping", orderNumber);
        }
    }

    private static Long parseOrderId(String rawOrderId, String orderNumber) {
        try {
            return Long.parseLong(rawOrderId);
        } catch (NumberFormatException ex) {
            log.warn("Invalid order id '{}' for order {}, skipping triage", rawOrderId, orderNumber);
            return null;
        }
    }

    private static void validateResult(AnomalyClassificationResult result) {
        if (result == null || result.classification() == null) {
            throw new IllegalStateException("AI classification returned no classification");
        }
        if (result.rationale() == null || result.rationale().isBlank()) {
            throw new IllegalStateException("AI classification returned empty rationale");
        }
        if (result.confidence() != null
            && (result.confidence().compareTo(BigDecimal.ZERO) < 0
                || result.confidence().compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalStateException("AI classification confidence out of range 0-1");
        }
    }

    private static String buildUserPrompt(OrderCreatedEvent event, Payment payment,
                                          boolean redactPii) {
        String customerLabel = redactPii ? "redacted" : event.getCustomerName();
        String emailLabel = redactPii ? maskEmail(event.getCustomerEmail()) : event.getCustomerEmail();
        String cardLast4 = redactPii ? "redacted"
            : (payment.getCardLast4() != null ? payment.getCardLast4() : "n/a");
        String gatewayRef = payment.getGatewayReference() != null
            ? (redactPii ? maskGatewayReference(payment.getGatewayReference()) : payment.getGatewayReference())
            : "n/a";

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
            customerLabel,
            emailLabel,
            event.getTotalAmount(),
            event.getShippingCity(),
            event.getItems().size(),
            payment.getStatus(),
            payment.getAmount(),
            gatewayRef,
            cardLast4
        );
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "redacted";
        }
        int at = email.indexOf('@');
        return "***" + email.substring(at);
    }

    private static String maskGatewayReference(String reference) {
        if (reference.length() <= 4) {
            return "****";
        }
        return "****" + reference.substring(reference.length() - 4);
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

    private sealed interface PrepareResult {
        record AlreadyTriaged() implements PrepareResult {}
        record NoPayment() implements PrepareResult {}
        record Ready(Payment payment) implements PrepareResult {}
    }
}
