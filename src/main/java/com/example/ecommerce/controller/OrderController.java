package com.example.ecommerce.controller;

import com.example.ecommerce.domain.enums.OrderStatus;
import com.example.ecommerce.dto.request.CheckoutRequest;
import com.example.ecommerce.dto.response.OrderDTO;
import com.example.ecommerce.service.CustomerLookupService;
import com.example.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final CustomerLookupService customerLookup;

    public OrderController(OrderService orderService,
                           CustomerLookupService customerLookup) {
        this.orderService = orderService;
        this.customerLookup = customerLookup;
    }

    // Checkout — create order from cart
    @PostMapping("/checkout")
    public ResponseEntity<OrderDTO> checkout(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody CheckoutRequest request) {
        Long customerId = customerLookup.requireCustomerId(user.getUsername());
        OrderDTO order = orderService.checkout(customerId, request);
        return ResponseEntity.status(201).body(order);
    }

    // Customer views their own orders
    @GetMapping
    public ResponseEntity<Page<OrderDTO>> getMyOrders(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = customerLookup.requireCustomerId(user.getUsername());
        return ResponseEntity.ok(
            orderService.getCustomerOrders(customerId, page, size));
    }

    // Customer views specific order
    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        Long customerId = customerLookup.requireCustomerId(user.getUsername());
        return ResponseEntity.ok(orderService.getOrder(id, customerId));
    }

    // Admin updates order status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }
}
