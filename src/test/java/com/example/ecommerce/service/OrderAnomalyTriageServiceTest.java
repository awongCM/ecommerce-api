package com.example.ecommerce.service;

import com.example.ecommerce.ai.StubChatModel;
import com.example.ecommerce.domain.Payment;
import com.example.ecommerce.domain.enums.AnomalyClassification;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import com.example.ecommerce.repository.OrderAnomalyTriageRepository;
import com.example.ecommerce.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAnomalyTriageServiceTest {

    @Mock private OrderAnomalyTriageRepository triageRepository;
    @Mock private PaymentRepository paymentRepository;

    private OrderAnomalyTriageService service;

    @BeforeEach
    void setUp() {
        StubChatModel stubChatModel = new StubChatModel();
        TransactionTemplate tx = passthroughTransactionTemplate();
        service = OrderAnomalyTriageService.forTests(
            triageRepository,
            paymentRepository,
            ChatClient.builder(stubChatModel),
            stubChatModel,
            tx,
            tx
        );
    }

    @Test
    void triage_shouldPersistClassification() {
        OrderCreatedEvent event = sampleEvent();
        Payment payment = samplePayment();

        when(triageRepository.existsByOrderId(1L)).thenReturn(false);
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        service.triage(event);

        var captor = ArgumentCaptor.forClass(com.example.ecommerce.domain.OrderAnomalyTriage.class);
        verify(triageRepository).save(captor.capture());
        assertThat(captor.getValue().getClassification()).isEqualTo(AnomalyClassification.OPS_REVIEW);
        assertThat(captor.getValue().getOrderNumber()).isEqualTo("ORD-TEST-001");
        assertThat(captor.getValue().getModelId()).isEqualTo(StubChatModel.MODEL_ID);
    }

    @Test
    void triage_whenAlreadyTriaged_shouldNoOp() {
        OrderCreatedEvent event = sampleEvent();
        when(triageRepository.existsByOrderId(1L)).thenReturn(true);

        service.triage(event);

        verify(triageRepository, never()).save(any());
        verify(paymentRepository, never()).findByOrderId(any());
    }

    @Test
    void triage_whenPaymentMissing_shouldNoOpWithoutThrowing() {
        OrderCreatedEvent event = sampleEvent();
        when(triageRepository.existsByOrderId(1L)).thenReturn(false);
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        service.triage(event);

        verify(triageRepository, never()).save(any());
    }

    @Test
    void triage_whenModelReturnsInvalidJson_shouldThrow() {
        OrderCreatedEvent event = sampleEvent();
        Payment payment = samplePayment();
        ChatModel badModel = new InvalidJsonChatModel();
        TransactionTemplate tx = passthroughTransactionTemplate();
        OrderAnomalyTriageService failingService = OrderAnomalyTriageService.forTests(
            triageRepository,
            paymentRepository,
            ChatClient.builder(badModel),
            badModel,
            tx,
            tx
        );

        when(triageRepository.existsByOrderId(1L)).thenReturn(false);
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> failingService.triage(event))
            .isInstanceOf(Exception.class);

        verify(triageRepository, never()).save(any());
    }

    @Test
    void triage_whenConcurrentInsert_shouldNotThrow() {
        OrderCreatedEvent event = sampleEvent();
        Payment payment = samplePayment();

        when(triageRepository.existsByOrderId(1L)).thenReturn(false);
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
        when(triageRepository.save(any())).thenThrow(new DataIntegrityViolationException("unique order_id"));

        service.triage(event);
    }

    @Test
    void triage_whenOrderIdInvalid_shouldNoOpWithoutThrowing() {
        OrderCreatedEvent event = new OrderCreatedEvent(
            "not-a-number",
            "ORD-BAD",
            "buyer@test.com",
            "Buyer",
            BigDecimal.TEN,
            List.of(),
            "Sydney"
        );

        service.triage(event);

        verify(triageRepository, never()).existsByOrderId(any());
        verify(triageRepository, never()).save(any());
    }

    private static OrderCreatedEvent sampleEvent() {
        return new OrderCreatedEvent(
            "1",
            "ORD-TEST-001",
            "buyer@test.com",
            "Buyer Test",
            new BigDecimal("59.98"),
            List.of(new OrderCreatedEvent.OrderItemEvent("Widget", 2, new BigDecimal("29.99"))),
            "Sydney"
        );
    }

    private static Payment samplePayment() {
        Payment payment = new Payment(null, new BigDecimal("59.98"), "pay-key-1");
        payment.markCaptured("gw-ref-123", "4242");
        return payment;
    }

    private static TransactionTemplate passthroughTransactionTemplate() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        TransactionStatus status = mock(TransactionStatus.class);
        lenient().doAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(status);
        }).when(template).execute(any(TransactionCallback.class));
        lenient().doAnswer(inv -> {
            Consumer<TransactionStatus> callback = inv.getArgument(0);
            callback.accept(status);
            return null;
        }).when(template).executeWithoutResult(any());
        return template;
    }

    /** Returns JSON without a valid classification enum value. */
    private static final class InvalidJsonChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            var generation = new Generation(
                new AssistantMessage("{\"classification\":\"NOT_A_LABEL\",\"confidence\":0.5,\"rationale\":\"bad\"}"));
            return new ChatResponse(List.of(generation));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().model("invalid").build();
        }
    }
}
