package com.example.ecommerce.payment;

/**
 * Sealed type representing every observable result of a payment capture attempt.
 * Use exhaustive pattern-matching switch on the caller side — no instanceof chains.
 */
public sealed interface PaymentOutcome
        permits PaymentOutcome.Captured,
                PaymentOutcome.GatewayUnavailable,
                PaymentOutcome.Failed {

    /** Payment was captured successfully. */
    record Captured(String gatewayReference, String cardLast4) implements PaymentOutcome {}

    /** Circuit is open or all retries exhausted — gateway is not reachable. */
    record GatewayUnavailable(String reason) implements PaymentOutcome {}

    /** Gateway reachable but returned a business failure (declined, invalid token, etc.). */
    record Failed(String reason) implements PaymentOutcome {}
}
