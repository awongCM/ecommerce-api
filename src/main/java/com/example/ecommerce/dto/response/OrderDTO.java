package com.example.ecommerce.dto.response;

import com.example.ecommerce.domain.Order;
import com.example.ecommerce.domain.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class OrderDTO {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private List<OrderItemDTO> items;
    private LocalDateTime createdAt;
    private String paymentStatus;

    public static OrderDTO from(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.id = order.getId();
        dto.orderNumber = order.getOrderNumber();
        dto.status = order.getStatus();
        dto.totalAmount = order.getTotalAmount();
        dto.createdAt = order.getCreatedAt();
        dto.items = order.getItems().stream()
            .map(OrderItemDTO::from)
            .collect(Collectors.toList());
        if (order.getPayment() != null) {
            dto.paymentStatus = order.getPayment().getStatus().name();
        }
        return dto;
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public List<OrderItemDTO> getItems() { return items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getPaymentStatus() { return paymentStatus; }

    public static class OrderItemDTO {
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;

        public static OrderItemDTO from(com.example.ecommerce.domain.OrderItem item) {
            OrderItemDTO dto = new OrderItemDTO();
            dto.productName = item.getProduct().getName();
            dto.quantity = item.getQuantity();
            dto.unitPrice = item.getUnitPrice();
            dto.subtotal = item.getSubtotal();
            return dto;
        }

        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public BigDecimal getSubtotal() { return subtotal; }
    }
}
