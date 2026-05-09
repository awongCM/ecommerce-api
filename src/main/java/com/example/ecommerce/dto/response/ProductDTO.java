package com.example.ecommerce.dto.response;

import com.example.ecommerce.domain.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String categoryName;
    private String sku;
    private boolean active;
    private LocalDateTime createdAt;

    public ProductDTO() {}

    public ProductDTO(Long id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    // Static factory — converts entity to DTO
    public static ProductDTO from(Product p) {
        ProductDTO dto = new ProductDTO();
        dto.id = p.getId();
        dto.name = p.getName();
        dto.description = p.getDescription();
        dto.price = p.getPrice();
        dto.stockQuantity = p.getStockQuantity();
        dto.categoryName = p.getCategory() != null ? p.getCategory().getName() : null;
        dto.sku = p.getSku();
        dto.active = p.isActive();
        dto.createdAt = p.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public Integer getStockQuantity() { return stockQuantity; }
    public String getCategoryName() { return categoryName; }
    public String getSku() { return sku; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
