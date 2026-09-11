package com.example.ecommerce.config;

import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.domain.enums.Role;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CartRepository cartRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AppProperties appProperties;
    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        bootstrap = new AdminBootstrap(
            appProperties, customerRepository, cartRepository, passwordEncoder);
    }

    @Test
    void bootstrap_whenAdminExists_shouldSkip() {
        when(customerRepository.countByRole(Role.ADMIN)).thenReturn(1L);
        appProperties.getAdmin().setEmail("admin@example.com");
        appProperties.getAdmin().setPassword("secret");

        bootstrap.bootstrapIfNeeded();

        verify(customerRepository, never()).save(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void bootstrap_whenCredentialsMissing_shouldSkip() {
        when(customerRepository.countByRole(Role.ADMIN)).thenReturn(0L);

        bootstrap.bootstrapIfNeeded();

        verify(customerRepository, never()).save(any());
    }

    @Test
    void bootstrap_whenNoAdmin_shouldCreateUserAndCart() {
        when(customerRepository.countByRole(Role.ADMIN)).thenReturn(0L);
        when(customerRepository.findByEmail("ops@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("s3cret")).thenReturn("hashed");
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        appProperties.getAdmin().setEmail("ops@example.com");
        appProperties.getAdmin().setPassword("s3cret");
        appProperties.getAdmin().setFirstName("Ops");
        appProperties.getAdmin().setLastName("Lead");

        bootstrap.bootstrapIfNeeded();

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("ops@example.com");
        assertThat(captor.getValue().getRoles()).contains(Role.ADMIN, Role.CUSTOMER);
        verify(cartRepository).save(any());
    }
}
