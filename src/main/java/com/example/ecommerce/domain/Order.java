package com.example.ecommerce.domain;

import com.example.ecommerce.domain.enums.OrderStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public-facing order number (not the DB PK)
    @Column(nullable = false, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @OneToMany(mappedBy = "order",
               cascade = CascadeType.ALL,
               orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_address_id")
    private Address shippingAddress;

    // Idempotency key — prevents duplicate orders on retry
    @Column(unique = true)
    private String idempotencyKey;

    @OneToOne(mappedBy = "order",
              cascade = CascadeType.ALL,
              fetch = FetchType.LAZY)
    private Payment payment;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    protected Order() {}

    public Order(Customer customer, Address shippingAddress,
                 BigDecimal totalAmount, String idempotencyKey) {
        this.customer = customer;
        this.shippingAddress = shippingAddress;
        this.totalAmount = totalAmount;
        this.idempotencyKey = idempotencyKey;
        this.orderNumber = "ORD-" + UUID.randomUUID().toString()
                                .replace("-","").substring(0,10).toUpperCase();
    }

    public void transitionTo(OrderStatus newStatus) {
        validateTransition(this.status, newStatus);
        this.status = newStatus;
    }

    // Enforce valid state machine transitions
    private void validateTransition(OrderStatus from, OrderStatus to) {
        boolean valid = switch (from) {
            case PENDING     -> to == OrderStatus.CONFIRMED  || to == OrderStatus.CANCELLED;
            case CONFIRMED   -> to == OrderStatus.PROCESSING || to == OrderStatus.CANCELLED;
            case PROCESSING  -> to == OrderStatus.SHIPPED;
            case SHIPPED     -> to == OrderStatus.DELIVERED;
            case DELIVERED   -> to == OrderStatus.REFUNDED;
            default          -> false;
        };
        if (!valid) {
            throw new IllegalStateException(
                "Invalid transition: " + from + " → " + to);
        }
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Customer getCustomer() { return customer; }
    public List<OrderItem> getItems() { return items; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Address getShippingAddress() { return shippingAddress; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment p) { this.payment = p; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
