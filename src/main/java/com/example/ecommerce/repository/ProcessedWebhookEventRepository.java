package com.example.ecommerce.repository;

import com.example.ecommerce.domain.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedWebhookEventRepository
        extends JpaRepository<ProcessedWebhookEvent, String> {
}
