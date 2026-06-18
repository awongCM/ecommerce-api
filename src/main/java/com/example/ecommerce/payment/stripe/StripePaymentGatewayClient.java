package com.example.ecommerce.payment.stripe;

import com.example.ecommerce.payment.PaymentCaptureResult;
import com.example.ecommerce.payment.PaymentGatewayClient;
import com.example.ecommerce.payment.PaymentGatewayException;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.net.RequestOptions;

import java.math.BigDecimal;

public class StripePaymentGatewayClient implements PaymentGatewayClient {

    private final StripeClient stripe;
    private final String defaultCurrency;

    public StripePaymentGatewayClient(StripeClient stripe, String defaultCurrency) {
        this.stripe = stripe;
        this.defaultCurrency = defaultCurrency;
    }

    @Override
    public PaymentCaptureResult capture(
            String paymentToken, BigDecimal amount, String currency, String idempotencyKey) {

        String cur = (currency != null && !currency.isBlank()) ? currency : defaultCurrency;
        long amountMinor = amount.movePointRight(2).longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountMinor)
            .setCurrency(cur)
            .setPaymentMethod(paymentToken)
            .setConfirm(true)
            .addExpand("payment_method")
            .putMetadata("idempotency_key", idempotencyKey)
            .build();

        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build();

        try {
            PaymentIntent intent = stripe.paymentIntents().create(params, options);

            if (!"succeeded".equals(intent.getStatus())) {
                throw new PaymentGatewayException(
                    "Payment not completed: " + intent.getStatus(), false);
            }

            return new PaymentCaptureResult(
                intent.getId(),
                StripePaymentSupport.extractLast4(intent),
                intent.getStatus());

        } catch (StripeException e) {
            boolean retryable = e instanceof ApiConnectionException
                || (e instanceof ApiException api && api.getStatusCode() >= 500);
            throw new PaymentGatewayException(e.getMessage(), retryable);
        }
    }
}
