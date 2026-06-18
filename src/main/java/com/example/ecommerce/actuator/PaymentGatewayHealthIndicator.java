package com.example.ecommerce.actuator;

import com.example.ecommerce.config.AppProperties;
import com.stripe.StripeClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {

    private final AppProperties properties;
    private final ObjectProvider<StripeClient> stripeClient;

    public PaymentGatewayHealthIndicator(
            AppProperties properties,
            ObjectProvider<StripeClient> stripeClient) {
        this.properties = properties;
        this.stripeClient = stripeClient;
    }

    @Override
    public Health health() {
        String provider = properties.getPaymentGateway().getProvider();
        if ("stripe".equalsIgnoreCase(provider)) {
            return stripeHealth();
        }
        return mockHealth();
    }

    private Health mockHealth() {
        return Health.up()
            .withDetail("provider", "mock")
            .withDetail("status", "in-process mock gateway")
            .build();
    }

    private Health stripeHealth() {
        String secretKey = properties.getStripe().getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            return Health.down()
                .withDetail("provider", "stripe")
                .withDetail("error", "STRIPE_SECRET_KEY not configured")
                .build();
        }

        StripeClient client = stripeClient.getIfAvailable();
        if (client == null) {
            return Health.down()
                .withDetail("provider", "stripe")
                .withDetail("error", "Stripe client bean not available")
                .build();
        }

        try {
            client.balance().retrieve();
            return Health.up()
                .withDetail("provider", "stripe")
                .withDetail("status", "reachable")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("provider", "stripe")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
