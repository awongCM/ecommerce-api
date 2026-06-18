package com.example.ecommerce.config;

import com.example.ecommerce.payment.PaymentGatewayClient;
import com.example.ecommerce.payment.mock.MockPaymentGatewayClient;
import com.example.ecommerce.payment.stripe.StripePaymentGatewayClient;
import com.stripe.StripeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentGatewayConfig {

    @Bean
    @ConditionalOnProperty(
        name = "app.payment-gateway.provider",
        havingValue = "mock",
        matchIfMissing = true)
    PaymentGatewayClient mockPaymentGatewayClient() {
        return new MockPaymentGatewayClient();
    }

    @Configuration
    @ConditionalOnProperty(name = "app.payment-gateway.provider", havingValue = "stripe")
    static class StripePaymentGatewayConfig {

        @Bean
        StripeClient stripeClient(AppProperties properties) {
            return new StripeClient(properties.getStripe().getSecretKey());
        }

        @Bean
        PaymentGatewayClient stripePaymentGatewayClient(
                StripeClient stripeClient, AppProperties properties) {
            return new StripePaymentGatewayClient(
                stripeClient, properties.getPaymentGateway().getCurrency());
        }
    }
}
