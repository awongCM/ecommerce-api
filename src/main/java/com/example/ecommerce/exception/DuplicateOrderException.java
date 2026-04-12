package com.example.ecommerce.exception;

public class DuplicateOrderException extends RuntimeException {
    private final String orderNumber;
    public DuplicateOrderException(String orderNumber) {
        super("Order already exists: " + orderNumber);
        this.orderNumber = orderNumber;
    }
    public String getOrderNumber() { return orderNumber; }
}
