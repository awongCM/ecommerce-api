package com.example.ecommerce.payment;

public class PaymentGatewayException extends RuntimeException {
    private final boolean retryable;

    public PaymentGatewayException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() { return retryable; }
}