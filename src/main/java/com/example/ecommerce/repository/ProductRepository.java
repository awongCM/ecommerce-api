package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);
    List<Product> findByActiveTrue();
    Page<Product> findByActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        WHERE p.active = true
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%',:term,'%'))
          OR LOWER(p.description) LIKE LOWER(CONCAT('%',:term,'%')))
        ORDER BY p.name ASC
    """)
    Page<Product> searchByTerm(@Param("term") String term, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true " +
           "AND p.price BETWEEN :minPrice AND :maxPrice")
    List<Product> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                   @Param("maxPrice") BigDecimal maxPrice);

    @Query("SELECT p FROM Product p WHERE p.active = true " +
           "AND p.stockQuantity <= :threshold")
    List<Product> findLowStockProducts(@Param("threshold") int threshold);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findByIdWithCategory(@Param("id") Long id);

    boolean existsBySku(String sku);
}
