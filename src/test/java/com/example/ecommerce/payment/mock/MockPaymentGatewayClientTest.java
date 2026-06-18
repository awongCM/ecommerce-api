package com.example.ecommerce.payment.mock;

import com.example.ecommerce.payment.PaymentCaptureResult;
import com.example.ecommerce.payment.PaymentGatewayException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentGatewayClientTest {

    private final MockPaymentGatewayClient client = new MockPaymentGatewayClient();

    @Test
    void capture_shouldReturnGatewayReference_whenTokenValid() {
        PaymentCaptureResult result = client.capture(
            "tok_valid", BigDecimal.TEN, "usd", "key-1");

        assertThat(result.gatewayReference()).startsWith("GW-");
        assertThat(result.cardLast4()).isEqualTo("4242");
        assertThat(result.rawStatus()).isEqualTo("succeeded");
    }

    @Test
    void capture_shouldRejectBlankToken() {
        assertThatThrownBy(() -> client.capture(" ", BigDecimal.ONE, "usd", "key-2"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("Invalid payment token");
    }

    @Test
    void capture_shouldSimulateFailure_whenTokenIsFail() {
        assertThatThrownBy(() -> client.capture("fail", BigDecimal.ONE, "usd", "key-3"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("Simulated gateway failure");
    }
}
