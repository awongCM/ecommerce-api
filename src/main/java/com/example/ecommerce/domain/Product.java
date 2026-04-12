package com.example.ecommerce.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String sku;

    private boolean active = true;

    @CreatedBy
    private String createdBy;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Optimistic locking — prevents two threads overselling stock simultaneously
    @Version
    private Long version;

    protected Product() {}

    public Product(String name, String description, BigDecimal price,
                   Integer stockQuantity, Category category, String sku) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.category = category;
        this.sku = sku;
    }

    public boolean hasStock(int quantity) {
        return this.stockQuantity >= quantity;
    }

    public void decrementStock(int quantity) {
        if (!hasStock(quantity)) {
            throw new IllegalStateException(
                "Insufficient stock for product: " + this.name);
        }
        this.stockQuantity -= quantity;
    }

    public void incrementStock(int quantity) {
        this.stockQuantity += quantity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal p) { this.price = p; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer q) { this.stockQuantity = q; }
    public Category getCategory() { return category; }
    public void setCategory(Category c) { this.category = c; }
    public String getSku() { return sku; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
}
