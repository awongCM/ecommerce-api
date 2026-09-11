package com.example.ecommerce.config;

import com.example.ecommerce.domain.Cart;
import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.domain.enums.Role;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first ADMIN when none exist. Docker/prod only — local {@code dev}
 * uses Flyway {@code db/dev} instead. Skips if {@code ADMIN_EMAIL}/{@code ADMIN_PASSWORD}
 * are unset so docker-compose still starts without secrets.
 */
@Slf4j
@Component
@Profile("docker")
public class AdminBootstrap implements ApplicationRunner {

    private final AppProperties appProperties;
    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AppProperties appProperties,
                          CustomerRepository customerRepository,
                          CartRepository cartRepository,
                          PasswordEncoder passwordEncoder) {
        this.appProperties = appProperties;
        this.customerRepository = customerRepository;
        this.cartRepository = cartRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrapIfNeeded();
    }

    void bootstrapIfNeeded() {
        if (customerRepository.countByRole(Role.ADMIN) > 0) {
            log.debug("Admin already exists, skipping bootstrap");
            return;
        }

        AppProperties.Admin cfg = appProperties.getAdmin();
        String email = cfg.getEmail() == null ? "" : cfg.getEmail().trim();
        String password = cfg.getPassword() == null ? "" : cfg.getPassword();
        if (email.isEmpty() || password.isBlank()) {
            log.warn("No ADMIN user exists. Set ADMIN_EMAIL and ADMIN_PASSWORD to bootstrap one, "
                + "or insert an admin row manually.");
            return;
        }

        Customer customer = customerRepository.findByEmail(email).orElseGet(() -> {
            Customer created = new Customer(
                blankToDefault(cfg.getFirstName(), "Admin"),
                blankToDefault(cfg.getLastName(), "User"),
                email,
                passwordEncoder.encode(password)
            );
            created.getRoles().add(Role.ADMIN);
            Customer saved = customerRepository.save(created);
            cartRepository.save(new Cart(saved));
            log.info("Bootstrapped first admin from env: {}", email);
            return saved;
        });

        if (!customer.getRoles().contains(Role.ADMIN)) {
            customer.getRoles().add(Role.ADMIN);
            customerRepository.save(customer);
            log.info("Granted ADMIN to existing account {}", email);
        }
    }

    private static String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
