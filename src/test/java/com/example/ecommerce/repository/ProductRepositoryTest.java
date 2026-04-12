package com.example.ecommerce.repository;

import com.example.ecommerce.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest  // Only loads JPA layer, uses H2 automatically
class ProductRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category("Electronics");
        entityManager.persist(category);
    }

    @Test
    void findBySku_shouldReturnProduct_whenSkuExists() {
        Product product = new Product("iPhone 15", "Latest iPhone",
            new BigDecimal("1299.00"), 50, category, "IPHONE-15");
        entityManager.persist(product);
        entityManager.flush();

        var found = productRepository.findBySku("IPHONE-15");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("iPhone 15");
    }

    @Test
    void searchByTerm_shouldFindProductsByNameAndDescription() {
        Product p1 = new Product("MacBook Pro", "Apple laptop for professionals",
            new BigDecimal("2499.00"), 30, category, "MBP-001");
        Product p2 = new Product("iPad Pro", "Professional tablet",
            new BigDecimal("999.00"), 45, category, "IPAD-001");
        Product p3 = new Product("Headphones", "Noise cancelling",
            new BigDecimal("299.00"), 100, category, "HEAD-001");
        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.persist(p3);
        entityManager.flush();

        var results = productRepository.searchByTerm(
            "professional", PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(2);
        assertThat(results.getContent())
            .extracting(Product::getName)
            .containsExactlyInAnyOrder("MacBook Pro", "iPad Pro");
    }

    @Test
    void findLowStockProducts_shouldReturnProductsBelowThreshold() {
        Product lowStock = new Product("Cable", "USB-C Cable",
            new BigDecimal("9.99"), 3, category, "CABLE-001");
        Product highStock = new Product("Case", "Phone Case",
            new BigDecimal("19.99"), 500, category, "CASE-001");
        entityManager.persist(lowStock);
        entityManager.persist(highStock);
        entityManager.flush();

        var result = productRepository.findLowStockProducts(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSku()).isEqualTo("CABLE-001");
    }

    @Test
    void existsBySku_shouldReturnTrue_whenSkuTaken() {
        Product product = new Product("Charger", "Fast charger",
            new BigDecimal("29.99"), 200, category, "CHARGE-001");
        entityManager.persist(product);
        entityManager.flush();

        assertThat(productRepository.existsBySku("CHARGE-001")).isTrue();
        assertThat(productRepository.existsBySku("CHARGE-999")).isFalse();
    }
}
