package com.example.ecommerce.repository;

import com.example.ecommerce.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeAndEntityId(
        String entityType, String entityId, Pageable pageable);

    List<AuditLog> findByPerformedByAndTimestampAfter(
        String performedBy, LocalDateTime since);
}
