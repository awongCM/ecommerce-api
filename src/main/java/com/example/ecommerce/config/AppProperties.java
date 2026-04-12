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

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public PaymentGateway getPaymentGateway() { return paymentGateway; }
    public void setPaymentGateway(PaymentGateway pg) { this.paymentGateway = pg; }
    public int getMaxCartItems() { return maxCartItems; }
    public void setMaxCartItems(int n) { this.maxCartItems = n; }
    public BigDecimal getMaxOrderAmount() { return maxOrderAmount; }
    public void setMaxOrderAmount(BigDecimal a) { this.maxOrderAmount = a; }
}
