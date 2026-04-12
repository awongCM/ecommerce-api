package com.example.ecommerce.controller;

import com.example.ecommerce.domain.enums.OrderStatus;
import com.example.ecommerce.dto.request.CheckoutRequest;
import com.example.ecommerce.dto.response.OrderDTO;
import com.example.ecommerce.repository.CustomerRepository;
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
    private final CustomerRepository customerRepo;

    public OrderController(OrderService orderService,
                           CustomerRepository customerRepo) {
        this.orderService = orderService;
        this.customerRepo = customerRepo;
    }

    // Checkout — create order from cart
    @PostMapping("/checkout")
    public ResponseEntity<OrderDTO> checkout(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody CheckoutRequest request) {
        Long customerId = resolveCustomerId(user.getUsername());
        OrderDTO order = orderService.checkout(customerId, request);
        return ResponseEntity.status(201).body(order);
    }

    // Customer views their own orders
    @GetMapping
    public ResponseEntity<Page<OrderDTO>> getMyOrders(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = resolveCustomerId(user.getUsername());
        return ResponseEntity.ok(
            orderService.getCustomerOrders(customerId, page, size));
    }

    // Customer views specific order
    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        Long customerId = resolveCustomerId(user.getUsername());
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

    private Long resolveCustomerId(String email) {
        return customerRepo.findByEmail(email)
            .orElseThrow(() ->
                new com.example.ecommerce.exception
                    .ResourceNotFoundException("Customer: " + email))
            .getId();
    }
}
