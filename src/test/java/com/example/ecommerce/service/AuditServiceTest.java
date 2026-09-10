package com.example.ecommerce.service;

import com.example.ecommerce.domain.AuditLog;
import com.example.ecommerce.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditService auditService;

    @BeforeEach
    void setUpSecurityAndMdc() {
        var auth = new UsernamePasswordAuthenticationToken(
            "alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        MDC.put("traceId", "trace-123");
    }

    @AfterEach
    void clearSecurityAndMdc() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void captureContext_shouldSnapshotActorAndTraceId() {
        AuditService.AuditContext context = auditService.captureContext();

        assertThat(context.actor()).isEqualTo("alice");
        assertThat(context.traceId()).isEqualTo("trace-123");
    }

    @Test
    void logSync_withExplicitContext_shouldPersistWithoutSecurityContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();

        auditService.logSync("Order", "42", "CHECKOUT", null, "ORD-1",
            "bob", "trace-456");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getPerformedBy()).isEqualTo("bob");
        assertThat(saved.getTraceId()).isEqualTo("trace-456");
        assertThat(saved.getAction()).isEqualTo("CHECKOUT");
    }
}
