package com.example.ecommerce.actuator;

import com.example.ecommerce.config.AppProperties;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {

    private final AppProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public PaymentGatewayHealthIndicator(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            // Ping the payment gateway health endpoint
            String healthUrl = properties.getPaymentGateway().getUrl() + "/health";
            restTemplate.getForObject(healthUrl, String.class);

            return Health.up()
                .withDetail("gateway", properties.getPaymentGateway().getUrl())
                .withDetail("status", "reachable")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("gateway", properties.getPaymentGateway().getUrl())
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
