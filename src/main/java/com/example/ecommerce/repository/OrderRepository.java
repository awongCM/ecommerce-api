package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Order;
import com.example.ecommerce.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    List<Order> findByStatus(OrderStatus status);

    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN FETCH o.items i
        LEFT JOIN FETCH i.product
        WHERE o.id = :id
    """)
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT SUM(o.totalAmount) FROM Order o " +
           "WHERE o.status = 'DELIVERED' " +
           "AND o.createdAt BETWEEN :from AND :to")
    Optional<java.math.BigDecimal> sumRevenueInRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    long countByStatus(OrderStatus status);
}
