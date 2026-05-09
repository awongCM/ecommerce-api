package com.example.ecommerce.service;

import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.domain.PasswordResetToken;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.exception.TokenExpiredException;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.repository.PasswordResetTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final CustomerRepository customerRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public PasswordResetService(CustomerRepository customerRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.customerRepository = customerRepository;
        this.tokenRepository    = tokenRepository;
        this.passwordEncoder    = passwordEncoder;
        this.emailService       = emailService;
    }

    /**
     * Initiates a password reset. If the email is not found we still return
     * successfully to avoid leaking whether an account exists.
     */
    @Transactional
    public void requestReset(String email) {
        customerRepository.findByEmail(email).ifPresent(customer -> {
            // Invalidate any existing tokens for this customer
            tokenRepository.deleteAllByCustomerId(customer.getId());

            PasswordResetToken resetToken = new PasswordResetToken(customer);
            tokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(customer.getEmail(), resetToken.getToken());
        });
    }

    /**
     * Validates the token and updates the customer's password.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("Password reset token not found"));

        if (resetToken.isUsed()) {
            throw new TokenExpiredException("This reset link has already been used");
        }

        if (resetToken.isExpired()) {
            throw new TokenExpiredException("This reset link has expired. Please request a new one");
        }

        Customer customer = resetToken.getCustomer();
        customer.setPasswordHash(passwordEncoder.encode(newPassword));
        customerRepository.save(customer);

        resetToken.markUsed();
        tokenRepository.save(resetToken);
    }
}
