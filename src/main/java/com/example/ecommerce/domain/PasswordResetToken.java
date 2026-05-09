package com.example.ecommerce.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    private static final int EXPIRY_HOURS = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    protected PasswordResetToken() {}

    public PasswordResetToken(Customer customer) {
        this.customer  = customer;
        this.token     = UUID.randomUUID().toString();
        this.expiresAt = LocalDateTime.now().plusHours(EXPIRY_HOURS);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public Long getId()                  { return id; }
    public Customer getCustomer()        { return customer; }
    public String getToken()             { return token; }
    public LocalDateTime getExpiresAt()  { return expiresAt; }
    public boolean isUsed()              { return used; }
    public void markUsed()               { this.used = true; }
}
