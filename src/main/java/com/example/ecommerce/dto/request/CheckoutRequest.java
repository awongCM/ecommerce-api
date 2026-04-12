package com.example.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CheckoutRequest {

    @NotNull(message = "Shipping address ID is required")
    private Long shippingAddressId;

    // Idempotency key — client generates this UUID to prevent double orders
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    // Payment details (in real life, use a tokenized card reference)
    @NotBlank(message = "Payment token is required")
    private String paymentToken;

    public Long getShippingAddressId() { return shippingAddressId; }
    public void setShippingAddressId(Long id) { this.shippingAddressId = id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String k) { this.idempotencyKey = k; }
    public String getPaymentToken() { return paymentToken; }
    public void setPaymentToken(String t) { this.paymentToken = t; }
}
