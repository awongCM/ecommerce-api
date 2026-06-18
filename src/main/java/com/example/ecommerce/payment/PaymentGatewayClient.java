package com.example.ecommerce.payment;

import java.math.BigDecimal;

public interface PaymentGatewayClient {

    /**
     * Charge (or confirm) using a client-supplied token.
     *
     * @param paymentToken   Stripe PaymentMethod id (pm_...) from Elements
     * @param amount         order total in major units (e.g. 49.99 USD)
     * @param currency       ISO 4217, e.g. "usd"
     * @param idempotencyKey checkout idempotency key — MUST be sent to Stripe
     */
    PaymentCaptureResult capture(
            String paymentToken,
            BigDecimal amount,
            String currency,
            String idempotencyKey);
}
