package com.example.ecommerce.service;

import com.example.ecommerce.domain.*;
import com.example.ecommerce.dto.request.AddToCartRequest;
import com.example.ecommerce.dto.response.CartDTO;
import com.example.ecommerce.exception.InsufficientStockException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public CartService(CartRepository cartRepository,
                       ProductRepository productRepository,
                       CustomerRepository customerRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public CartDTO getCart(Long customerId) {
        System.out.println("getCartService" + customerId);
        System.out.println("cartRepository" + cartRepository);
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Cart for customer", customerId));
        return CartDTO.from(cart);
    }

    @Transactional
    public CartDTO addItem(Long customerId, AddToCartRequest request) {
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Cart for customer", customerId));

        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() ->
                new ResourceNotFoundException("Product", request.getProductId()));

        if (!product.isActive()) {
            throw new IllegalStateException(
                "Product is no longer available: " + product.getName());
        }

        if (!product.hasStock(request.getQuantity())) {
            throw new InsufficientStockException(
                product.getName(), request.getQuantity(),
                product.getStockQuantity());
        }

        cart.addItem(product, request.getQuantity());
        return CartDTO.from(cartRepository.save(cart));
    }

    @Transactional
    public CartDTO removeItem(Long customerId, Long productId) {
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Cart for customer", customerId));
        cart.removeItem(productId);
        return CartDTO.from(cartRepository.save(cart));
    }

    @Transactional
    public void clearCart(Long customerId) {
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Cart for customer", customerId));
        cart.clear();
        cartRepository.save(cart);
    }
}
