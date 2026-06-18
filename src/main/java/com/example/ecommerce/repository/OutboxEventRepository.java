package com.example.ecommerce.repository;

import com.example.ecommerce.domain.OutboxEvent;
import com.example.ecommerce.domain.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
        OutboxStatus status, Pageable pageable);
}
