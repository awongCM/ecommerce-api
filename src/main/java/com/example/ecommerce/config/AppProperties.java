package com.example.ecommerce.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    private Jwt jwt = new Jwt();
    private PaymentGateway paymentGateway = new PaymentGateway();
    private Stripe stripe = new Stripe();
    private Features features = new Features();
    private Admin admin = new Admin();
    private int maxCartItems = 50;
    private BigDecimal maxOrderAmount = BigDecimal.valueOf(10000);

    public static class Jwt {
        @NotBlank
        private String secret;
        @Positive
        private long expiryMs = 86400000L;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpiryMs() { return expiryMs; }
        public void setExpiryMs(long expiryMs) { this.expiryMs = expiryMs; }
    }

    public static class PaymentGateway {
        private String url;
        private int timeoutMs = 5000;
        private String provider = "mock";
        private String currency = "usd";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }

    public static class Stripe {
        private String secretKey;
        private String webhookSecret;

        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getWebhookSecret() { return webhookSecret; }
        public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    }

    public static class Features {
        private boolean orderAnomalyTriage = false;

        public boolean isOrderAnomalyTriage() { return orderAnomalyTriage; }
        public void setOrderAnomalyTriage(boolean orderAnomalyTriage) {
            this.orderAnomalyTriage = orderAnomalyTriage;
        }
    }

    /** First-admin bootstrap for the docker profile (env-driven; never required). */
    public static class Admin {
        private String email = "";
        private String password = "";
        private String firstName = "Admin";
        private String lastName = "User";

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
    }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public PaymentGateway getPaymentGateway() { return paymentGateway; }
    public void setPaymentGateway(PaymentGateway pg) { this.paymentGateway = pg; }
    public Stripe getStripe() { return stripe; }
    public void setStripe(Stripe stripe) { this.stripe = stripe; }
    public Features getFeatures() { return features; }
    public void setFeatures(Features features) { this.features = features; }
    public Admin getAdmin() { return admin; }
    public void setAdmin(Admin admin) { this.admin = admin; }
    public int getMaxCartItems() { return maxCartItems; }
    public void setMaxCartItems(int n) { this.maxCartItems = n; }
    public BigDecimal getMaxOrderAmount() { return maxOrderAmount; }
    public void setMaxOrderAmount(BigDecimal a) { this.maxOrderAmount = a; }
}
