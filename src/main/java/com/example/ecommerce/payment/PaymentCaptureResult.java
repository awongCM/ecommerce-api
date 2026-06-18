package com.example.ecommerce.payment;

public record PaymentCaptureResult(
        String gatewayReference,   // Stripe PaymentIntent id: pi_...
        String cardLast4,          // nullable for wallets
        String rawStatus           // e.g. "succeeded" — optional debug/audit
) {}