package com.example.ecommerce.ai;

import com.example.ecommerce.domain.enums.AnomalyClassification;
import java.math.BigDecimal;

/** Structured output from the anomaly triage LLM call. */
public record AnomalyClassificationResult(
    AnomalyClassification classification,
    BigDecimal confidence,
    String rationale
) {}
