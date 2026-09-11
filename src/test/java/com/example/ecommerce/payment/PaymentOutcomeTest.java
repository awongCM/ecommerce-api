package com.example.ecommerce.payment;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentOutcomeTest {

    @Test
    void captured_recordRetainsFields() {
        var outcome = new PaymentOutcome.Captured("pi_123", "4242");
        assertThat(outcome.gatewayReference()).isEqualTo("pi_123");
        assertThat(outcome.cardLast4()).isEqualTo("4242");
    }

    @Test
    void patternSwitch_isExhaustive() {
        PaymentOutcome outcome = new PaymentOutcome.GatewayUnavailable("circuit open");
        String label = switch (outcome) {
            case PaymentOutcome.Captured c       -> "captured:" + c.gatewayReference();
            case PaymentOutcome.GatewayUnavailable u -> "unavailable:" + u.reason();
            case PaymentOutcome.Failed f         -> "failed:" + f.reason();
        };
        assertThat(label).startsWith("unavailable:");
    }

    @Test
    void failed_recordRetainsReason() {
        var outcome = new PaymentOutcome.Failed("card_declined");
        assertThat(outcome.reason()).isEqualTo("card_declined");
    }
}
