package com.example.ecommerce.service;

import com.example.ecommerce.domain.*;
import com.example.ecommerce.domain.enums.OrderStatus;
import com.example.ecommerce.dto.request.CheckoutRequest;
import com.example.ecommerce.dto.response.OrderDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.payment.PaymentOutcome;
import com.example.ecommerce.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final OutboxService outboxService;

    public OrderService(OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        CartRepository cartRepository,
                        InventoryService inventoryService,
                        PaymentService paymentService,
                        AuditService auditService,
                        OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.cartRepository = cartRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.auditService = auditService;
        this.outboxService = outboxService;
    }

    /**
     * Complete checkout flow:
     * 1. Check idempotency (prevent duplicate orders)
     * 2. Load customer's cart
     * 3. Reserve inventory for each item
     * 4. Create the order
     * 5. Process payment (with circuit breaker)
     * 6. Clear the cart
     * 7. Enqueue order-created event (transactional outbox)
     * 8. Log to audit trail
     */
    @Transactional
    public OrderDTO checkout(Long customerId, CheckoutRequest request) {
        // 1. Idempotency check — same key = same response
        return orderRepository
            .findByIdempotencyKey(request.getIdempotencyKey())
            .map(existingOrder -> {
                // Order already processed — return it without duplicating
                return OrderDTO.from(existingOrder);
            })
            .orElseGet(() -> processNewCheckout(customerId, request));
    }

    private OrderDTO processNewCheckout(Long customerId, CheckoutRequest request) {
        // 2. Load customer with cart in one query
        Customer customer = customerRepository.findByIdWithCart(customerId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Customer", customerId));

        Cart cart = customer.getCart();
        if (cart == null || cart.getItems().isEmpty()) {
            throw new IllegalStateException(
                "Cannot checkout with an empty cart");
        }

        Address shippingAddress = customer.getAddresses().stream()
            .filter(a -> Objects.equals(a.getId(), request.getShippingAddressId()))
            .findFirst()
            .orElseThrow(() ->
                new ResourceNotFoundException("Address",
                    request.getShippingAddressId()));

        // 3. Reserve inventory (with optimistic locking + retry)
        for (CartItem item : cart.getItems()) {
            inventoryService.reserveStock(
                item.getProduct().getId(), item.getQuantity());
        }

        // 4. Create the order
        Order order = new Order(customer, shippingAddress,
            cart.getTotalPrice(), request.getIdempotencyKey());

        for (CartItem item : cart.getItems()) {
            order.getItems().add(
                new OrderItem(order, item.getProduct(), item.getQuantity()));
        }
        order = orderRepository.save(order);

        // 5. Process payment (circuit breaker lives inside PaymentService)
        PaymentOutcome outcome = paymentService.processPayment(order, request.getPaymentToken());

        switch (outcome) {
            case PaymentOutcome.Captured c -> {
                order.transitionTo(OrderStatus.CONFIRMED);
                log.info("Checkout captured, ref={}", c.gatewayReference());
            }
            case PaymentOutcome.GatewayUnavailable u -> {
                for (CartItem item : cart.getItems()) {
                    inventoryService.releaseStock(item.getProduct().getId(), item.getQuantity());
                }
                order.transitionTo(OrderStatus.CANCELLED);
                orderRepository.save(order);
                throw new IllegalStateException("Payment gateway unavailable: " + u.reason());
            }
            case PaymentOutcome.Failed f -> {
                for (CartItem item : cart.getItems()) {
                    inventoryService.releaseStock(item.getProduct().getId(), item.getQuantity());
                }
                order.transitionTo(OrderStatus.CANCELLED);
                orderRepository.save(order);
                throw new IllegalStateException("Payment failed: " + f.reason());
            }
        }

        // 6. Clear the cart
        cart.clear();
        cartRepository.save(cart);

        Order savedOrder = orderRepository.save(order);

        // 7. Outbox stays in the checkout transaction (transactional outbox pattern).
        outboxService.enqueueOrderCreated(savedOrder);

        // 8. Snapshot request-thread context, then fan out after commit on virtual threads.
        AuditService.AuditContext auditContext = auditService.captureContext();
        registerPostCheckoutAfterCommit(savedOrder, customerId, auditContext);

        return OrderDTO.from(savedOrder);
    }

    private void registerPostCheckoutAfterCommit(Order savedOrder, Long customerId,
                                                 AuditService.AuditContext auditContext) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    postCheckoutTasks(savedOrder, customerId, auditContext);
                }
            });
    }

    /**
     * Runs post-commit tasks in parallel using virtual threads via StructuredTaskScope.
     * Outbox enqueue stays in {@link #processNewCheckout} so it commits with the order;
     * only independent, non-transactional work belongs here.
     */
    private void postCheckoutTasks(Order savedOrder, Long customerId,
                                   AuditService.AuditContext auditContext) {
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow(),
                cfg -> cfg.withThreadFactory(Thread.ofVirtual().factory()))) {
            scope.fork(() -> auditService.logSync(
                "Order", savedOrder.getId().toString(),
                "CHECKOUT", null, savedOrder.getOrderNumber(),
                auditContext.actor(), auditContext.traceId()));
            scope.fork(() -> log.info(
                "Checkout notification for order {} (customer {})",
                savedOrder.getOrderNumber(), customerId));
            scope.join();
        } catch (Exception e) {
            log.warn("Post-checkout tasks partially failed for order {}: {}",
                savedOrder.getOrderNumber(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderDTO> getCustomerOrders(Long customerId, int page, int size) {
        return orderRepository
            .findByCustomerIdOrderByCreatedAtDesc(
                customerId, PageRequest.of(page, size))
            .map(OrderDTO::from);
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrder(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdWithItems(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // Customers can only view their own orders
        if (!Objects.equals(order.getCustomer().getId(), customerId)) {
            throw new AccessDeniedException("Not your order");
        }

        return OrderDTO.from(order);
    }

    @Transactional
    public OrderDTO updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        String oldStatus = order.getStatus().name();
        order.transitionTo(newStatus);   // validates state machine
        orderRepository.save(order);

        auditService.log("Order", orderId.toString(),
            "STATUS_CHANGED", oldStatus, newStatus.name());

        return OrderDTO.from(order);
    }
}
