package com.example.ecommerce.domain;

import com.example.ecommerce.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    // The idempotency key sent to the payment gateway
    @Column(unique = true, nullable = false)
    private String idempotencyKey;

    // Reference ID returned by the payment gateway
    private String gatewayReference;

    // Masked card number — store ONLY last 4 digits
    private String cardLast4;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    protected Payment() {}

    public Payment(Order order, BigDecimal amount, String idempotencyKey) {
        this.order = order;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public void markCaptured(String gatewayReference, String cardLast4) {
        this.status = PaymentStatus.CAPTURED;
        this.gatewayReference = gatewayReference;
        this.cardLast4 = cardLast4;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus s) { this.status = s; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getGatewayReference() { return gatewayReference; }
    public String getCardLast4() { return cardLast4; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
