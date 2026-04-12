package com.example.ecommerce.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255)
    private String name;

    private String description;

    @NotNull
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    @NotNull
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stockQuantity;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    private String sku;

    // Getters and setters
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal p) { this.price = p; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer q) { this.stockQuantity = q; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long c) { this.categoryId = c; }
    public String getSku() { return sku; }
    public void setSku(String s) { this.sku = s; }
}
