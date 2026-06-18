package com.example.ecommerce.payment.mock;

import com.example.ecommerce.payment.PaymentCaptureResult;
import com.example.ecommerce.payment.PaymentGatewayClient;
import com.example.ecommerce.payment.PaymentGatewayException;

import java.math.BigDecimal;

public class MockPaymentGatewayClient implements PaymentGatewayClient {

    @Override
    public PaymentCaptureResult capture(
            String paymentToken, BigDecimal amount, String currency, String idempotencyKey) {
        if (paymentToken == null || paymentToken.isBlank()) {
            throw new PaymentGatewayException("Invalid payment token", false);
        }
        if ("fail".equalsIgnoreCase(paymentToken)) {
            throw new PaymentGatewayException("Simulated gateway failure", false);
        }
        return new PaymentCaptureResult(
            "GW-" + System.currentTimeMillis(), "4242", "succeeded");
    }
}
