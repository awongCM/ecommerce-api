package com.example.ecommerce.service;

import com.example.ecommerce.domain.Category;
import com.example.ecommerce.domain.Product;
import com.example.ecommerce.dto.request.CreateProductRequest;
import com.example.ecommerce.dto.response.ProductDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.ProductRepository;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    public ProductService(ProductRepository productRepository,
                          EntityManager entityManager) {
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @RateLimiter(name = "productSearch",
                 fallbackMethod = "searchFallback")
    public Page<ProductDTO> search(String term, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
            Sort.by("name").ascending());
        return productRepository.searchByTerm(term, pageable)
            .map(ProductDTO::from);
    }

    // Fallback when rate limit exceeded
    public Page<ProductDTO> searchFallback(String term, int page, int size,
            io.github.resilience4j.ratelimiter.RequestNotPermitted ex) {
        throw new IllegalStateException(
            "Search rate limit exceeded. Please try again shortly.");
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> findByCategory(Long categoryId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
            Sort.by("name").ascending());
        return productRepository
            .findByActiveTrueAndCategoryId(categoryId, pageable)
            .map(ProductDTO::from);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public ProductDTO findById(Long id) {
        Product product = productRepository.findByIdWithCategory(id)
            .orElseThrow(() ->
                new ResourceNotFoundException("Product", id));
        return ProductDTO.from(product);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#result.id")
    public ProductDTO createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new IllegalStateException(
                "Product with SKU already exists: " + request.getSku());
        }

        // Get category reference without loading it fully
        Category category = entityManager.getReference(
            Category.class, request.getCategoryId());

        Product product = new Product(
            request.getName(),
            request.getDescription(),
            request.getPrice(),
            request.getStockQuantity(),
            category,
            request.getSku()
        );

        return ProductDTO.from(productRepository.save(product));
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductDTO updateProduct(Long id, CreateProductRequest request) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());

        return ProductDTO.from(productRepository.save(product));
    }

    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setActive(false);   // soft delete
        productRepository.save(product);
    }

    // Getter used by InventoryEndpoint actuator
    @Transactional(readOnly = true)
    public java.util.List<ProductDTO> getLowStockProducts(int threshold) {
        return productRepository.findLowStockProducts(threshold)
            .stream().map(ProductDTO::from)
            .toList();
    }
}
