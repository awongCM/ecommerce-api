package com.example.ecommerce.repository;

import com.example.ecommerce.domain.OrderAnomalyTriage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderAnomalyTriageRepository extends JpaRepository<OrderAnomalyTriage, Long> {

    boolean existsByOrderId(Long orderId);
}
