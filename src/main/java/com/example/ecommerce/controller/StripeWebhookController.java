package com.example.ecommerce.controller;

import com.example.ecommerce.payment.stripe.StripeWebhookService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/stripe")
@ConditionalOnProperty(name = "app.payment-gateway.provider", havingValue = "stripe")
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        webhookService.handle(payload, sigHeader);
        return ResponseEntity.ok().build();
    }
}
