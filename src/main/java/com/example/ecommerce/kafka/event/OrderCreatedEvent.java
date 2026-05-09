package com.example.ecommerce.kafka.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Plain Java object — serialized to JSON by Kafka
public class OrderCreatedEvent {
    private String orderId;
    private String orderNumber;
    private String customerEmail;
    private String customerName;
    private BigDecimal totalAmount;
    private List<OrderItemEvent> items;
    private String shippingCity;
    private LocalDateTime createdAt;

    public OrderCreatedEvent() {}

    public OrderCreatedEvent(String orderId, String orderNumber,
                             String customerEmail, String customerName,
                             BigDecimal totalAmount, List<OrderItemEvent> items,
                             String shippingCity) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerEmail = customerEmail;
        this.customerName = customerName;
        this.totalAmount = totalAmount;
        this.items = items;
        this.shippingCity = shippingCity;
        this.createdAt = LocalDateTime.now();
    }

    public static class OrderItemEvent {
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;

        public OrderItemEvent() {}

        public OrderItemEvent(String productName, int quantity,
                              java.math.BigDecimal unitPrice) {
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public java.math.BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(java.math.BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }

    // Getters
    public String getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public List<OrderItemEvent> getItems() { return items; }
    public String getShippingCity() { return shippingCity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
