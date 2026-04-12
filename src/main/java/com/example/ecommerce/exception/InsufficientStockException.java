package com.example.ecommerce.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName, int requested, int available) {
        super(String.format("Insufficient stock for '%s': requested %d, available %d",
            productName, requested, available));
    }
}
