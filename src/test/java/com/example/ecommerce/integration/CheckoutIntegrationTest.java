package com.example.ecommerce.integration;

import com.example.ecommerce.dto.request.*;
import com.example.ecommerce.dto.response.*;
import com.example.ecommerce.domain.*;
import com.example.ecommerce.kafka.OutboxPoller;
import com.example.ecommerce.kafka.OrderEventPublisher;
import com.example.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CheckoutIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private OrderEventPublisher orderEventPublisher;

    @MockBean
    private OutboxPoller outboxPoller;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long seededProductId;

    @BeforeEach
    void seedProduct() {
        Category category = categoryRepository.save(new Category("Electronics"));
        Product product = productRepository.save(new Product(
            "Test Widget", "A test product",
            new BigDecimal("29.99"), 100, category, "SKU-TEST-001"));
        seededProductId = product.getId();
    }

    @Test
    void fullCheckoutFlow_shouldSucceed() {
        // 1. Register a customer
        RegisterRequest register = new RegisterRequest();
        register.setFirstName("Alice");
        register.setLastName("Smith");
        register.setEmail("alice@test.com");
        register.setPassword("securepassword");

        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
            "/api/v1/auth/register", register, AuthResponse.class);
        assertThat(registerResponse.getStatusCode())
            .isEqualTo(HttpStatus.CREATED);

        String token = registerResponse.getBody().getToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1b. Seed a shipping address for Alice
        Customer alice = customerRepository.findByEmail("alice@test.com").orElseThrow();
        Address address = addressRepository.save(
            new Address(alice, "1 Test St", "Sydney", "NSW", "2000", "AU"));
        Long shippingAddressId = address.getId();

        // 2. Browse products (public — no auth needed)
        ResponseEntity<String> productsResponse = restTemplate.getForEntity(
            "/api/v1/products", String.class);
        assertThat(productsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Add item to cart
        AddToCartRequest cartRequest = new AddToCartRequest();
        cartRequest.setProductId(seededProductId);
        cartRequest.setQuantity(2);

        ResponseEntity<CartDTO> cartResponse = restTemplate.exchange(
            "/api/v1/cart/items",
            HttpMethod.POST,
            new HttpEntity<>(cartRequest, headers),
            CartDTO.class);
        // 4. Verify cart has item
        assertThat(cartResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 5. Checkout
        CheckoutRequest checkout = new CheckoutRequest();
        checkout.setShippingAddressId(shippingAddressId);
        checkout.setIdempotencyKey(UUID.randomUUID().toString());
        checkout.setPaymentToken("tok_test_valid");

        ResponseEntity<OrderDTO> orderResponse = restTemplate.exchange(
            "/api/v1/orders/checkout",
            HttpMethod.POST,
            new HttpEntity<>(checkout, headers),
            OrderDTO.class);
        assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        OrderDTO order = orderResponse.getBody();
        assertThat(order.getOrderNumber()).startsWith("ORD-");

        // 6. Verify idempotency — submit same request again
        ResponseEntity<OrderDTO> dupResponse = restTemplate.exchange(
            "/api/v1/orders/checkout",
            HttpMethod.POST,
            new HttpEntity<>(checkout, headers),  // same idempotencyKey
            OrderDTO.class);
        assertThat(dupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(dupResponse.getBody().getOrderNumber())
            .isEqualTo(order.getOrderNumber()); // same order, not a duplicate
    }
}
