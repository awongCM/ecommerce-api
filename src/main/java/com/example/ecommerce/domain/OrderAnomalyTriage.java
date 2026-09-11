package com.example.ecommerce.domain;

import com.example.ecommerce.domain.enums.AnomalyClassification;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_anomaly_triage")
public class OrderAnomalyTriage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalyClassification classification;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(length = 1000)
    private String rationale;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OrderAnomalyTriage() {}

    public OrderAnomalyTriage(Long orderId, String orderNumber,
                              AnomalyClassification classification,
                              BigDecimal confidence, String rationale,
                              String modelId) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.classification = classification;
        this.confidence = confidence;
        this.rationale = rationale;
        this.modelId = modelId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public AnomalyClassification getClassification() { return classification; }
    public BigDecimal getConfidence() { return confidence; }
    public String getRationale() { return rationale; }
    public String getModelId() { return modelId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
