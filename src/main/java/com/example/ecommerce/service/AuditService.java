package com.example.ecommerce.service;

import com.example.ecommerce.domain.AuditLog;
import com.example.ecommerce.repository.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // @Async — don't let audit logging slow down the main request
    @Async
    public void log(String entityType, String entityId,
                    String action, String oldValue, String newValue) {
        String actor = getCurrentUser();
        AuditLog entry = new AuditLog(entityType, entityId,
            action, actor, oldValue, newValue);
        entry.setTraceId(MDC.get("traceId"));
        auditLogRepository.save(entry);
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "system";
    }
}
