package com.example.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100)
    private String name;

    private String description;

    private Long parentId;

    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long p) { this.parentId = p; }
}
