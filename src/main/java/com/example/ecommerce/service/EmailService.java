package com.example.ecommerce.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String appBaseUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from}") String fromAddress,
                        @Value("${app.mail.base-url}") String appBaseUrl) {
        this.mailSender  = mailSender;
        this.fromAddress = fromAddress;
        this.appBaseUrl  = appBaseUrl;
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = appBaseUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your password");
        message.setText(
            "Hi,\n\n" +
            "We received a request to reset your password. Click the link below within 1 hour:\n\n" +
            resetLink + "\n\n" +
            "If you did not request this, you can safely ignore this email.\n\n" +
            "The ecommerce team"
        );

        try {
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Could not send reset email. Please try again later.", e);
        }
    }
}
