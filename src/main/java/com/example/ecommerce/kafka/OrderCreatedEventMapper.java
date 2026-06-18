package com.example.ecommerce.kafka;

import com.example.ecommerce.domain.Order;
import com.example.ecommerce.kafka.event.OrderCreatedEvent;
import java.util.stream.Collectors;

public final class OrderCreatedEventMapper {

    private OrderCreatedEventMapper() {}

    public static OrderCreatedEvent fromOrder(Order order) {
        var items = order.getItems().stream()
            .map(item -> new OrderCreatedEvent.OrderItemEvent(
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice()
            ))
            .collect(Collectors.toList());

        String shippingCity = order.getShippingAddress() != null
            ? order.getShippingAddress().getCity() : "Unknown";

        return new OrderCreatedEvent(
            order.getId().toString(),
            order.getOrderNumber(),
            order.getCustomer().getEmail(),
            order.getCustomer().getFullName(),
            order.getTotalAmount(),
            items,
            shippingCity
        );
    }
}
