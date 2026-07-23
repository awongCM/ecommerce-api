package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.AddToCartRequest;
import com.example.ecommerce.dto.response.CartDTO;
import com.example.ecommerce.service.CartService;
import com.example.ecommerce.service.CustomerLookupService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;
    private final CustomerLookupService customerLookup;

    public CartController(CartService cartService,
                          CustomerLookupService customerLookup) {
        this.cartService = cartService;
        this.customerLookup = customerLookup;
    }

    @GetMapping
    public ResponseEntity<CartDTO> getCart(
            @AuthenticationPrincipal UserDetails user) {
        log.info("getCart-controller {}", user.getUsername());
        Long customerId = customerLookup.requireCustomerId(user.getUsername());
        log.info("customerId-controller {}", customerId);

        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDTO> addItem(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody AddToCartRequest request) {
        log.info("addItem-controller {}", request);
        Long customerId = customerLookup.requireCustomerId(user.getUsername());
        log.info("customerId-controller {}", customerId);

        return ResponseEntity.ok(cartService.addItem(customerId, request));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartDTO> removeItem(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long productId) {
        Long customerId = customerLookup.requireCustomerId(user.getUsername());
        return ResponseEntity.ok(cartService.removeItem(customerId, productId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            @AuthenticationPrincipal UserDetails user) {
        Long customerId = customerLookup.requireCustomerId(user.getUsername());
        cartService.clearCart(customerId);
        return ResponseEntity.noContent().build();
    }
}
