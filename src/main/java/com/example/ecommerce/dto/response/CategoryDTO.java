package com.example.ecommerce.dto.response;

import com.example.ecommerce.domain.Category;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class CategoryDTO {
    private Long id;
    private String name;
    private String description;
    private String parentName;
    private List<CategoryDTO> subcategories;

    public CategoryDTO() {}

    public CategoryDTO(Long id, String name, String description, String parentName) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.parentName = parentName;
    }

    // Static factory — converts entity to DTO
    public static CategoryDTO from(Category c) {
        CategoryDTO dto = new CategoryDTO();
        dto.id = c.getId();
        dto.name = c.getName();
        dto.description = c.getDescription();
        dto.parentName = c.getParent() != null ? c.getParent().getName() : null;
        dto.subcategories = c.getSubcategories().stream()
            .map(CategoryDTO::from)
            .collect(Collectors.toList());
        return dto;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getParentName() { return parentName; }
    public List<CategoryDTO> getSubcategories() { return subcategories; }
}
