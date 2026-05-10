package com.example.ecommerce.service;

import com.example.ecommerce.domain.Category;
import com.example.ecommerce.dto.request.CreateCategoryRequest;
import com.example.ecommerce.dto.response.CategoryDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CategoryRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<CategoryDTO> listTopLevel(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return categoryRepository.findByParentIsNull(pageable)
            .map(CategoryDTO::from);
    }

    @Transactional(readOnly = true)
    public Page<CategoryDTO> listSubcategories(Long parentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return categoryRepository.findByParentId(parentId, pageable)
            .map(CategoryDTO::from);
    }

    @Transactional(readOnly = true)
    public CategoryDTO getById(Long id) {
        return categoryRepository.findByIdWithSubcategories(id)
            .map(CategoryDTO::from)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Transactional
    public CategoryDTO createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new IllegalStateException(
                "Category already exists: " + request.getName());
        }

        Category category = new Category(request.getName());
        category.setDescription(request.getDescription());

        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getParentId()));
            category.setParent(parent);
        }

        return CategoryDTO.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CreateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getParentId()));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        return CategoryDTO.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        categoryRepository.delete(category);
    }
}
