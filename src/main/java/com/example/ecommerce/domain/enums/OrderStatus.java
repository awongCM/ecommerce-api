package com.example.ecommerce.domain.enums;

public enum OrderStatus {
    PENDING,        // just created
    CONFIRMED,      // payment authorised
    PROCESSING,     // being packed/shipped
    SHIPPED,        // with courier
    DELIVERED,      // received
    CANCELLED,      // cancelled before shipping
    REFUNDED        // money returned
}
