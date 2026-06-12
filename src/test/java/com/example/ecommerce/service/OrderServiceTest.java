package com.example.ecommerce.service;

import com.example.ecommerce.domain.*;
import com.example.ecommerce.domain.enums.OrderStatus;
import com.example.ecommerce.dto.request.CheckoutRequest;
import com.example.ecommerce.dto.response.OrderDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.kafka.OrderEventPublisher;
import com.example.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CartRepository cartRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PaymentService paymentService;
    @Mock private AuditService auditService;
    @Mock private OrderEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private Customer testCustomer;
    private Product testProduct;
    private Cart testCart;
    private Address testAddress;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer("John", "Doe",
            "john@example.com", "hashedPassword");
        testProduct = new Product("Laptop", "Gaming laptop",
            new BigDecimal("999.99"), 10,
            new Category("Electronics"), "LAP-001");

        testProduct.setId(42L);

        testCart = new Cart(testCustomer);
        testCart.addItem(testProduct, 2);

        testAddress = new Address(testCustomer,
            "123 Main St", "Sydney", "NSW", "2000", "AU");
        testCustomer.getAddresses().add(testAddress);
        testCustomer.setCart(testCart);
    }

    @Test
    void checkout_shouldCreateOrderSuccessfully() {
        // Arrange
        CheckoutRequest request = new CheckoutRequest();
        request.setShippingAddressId(null); // matches testAddress (unpersisted, id=null)
        request.setIdempotencyKey("unique-key-123");
        request.setPaymentToken("tok_valid");

        when(orderRepository.findByIdempotencyKey("unique-key-123"))
            .thenReturn(Optional.empty());
        
        when(customerRepository.findByIdWithCart(1L))
            .thenReturn(Optional.of(testCustomer));

        Order savedOrder = mock(Order.class);
        when(savedOrder.getId()).thenReturn(1L);
        when(savedOrder.getOrderNumber()).thenReturn("ORD-ABC123");
        when(savedOrder.getItems()).thenReturn(java.util.List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderDTO result = orderService.checkout(1L, request);

        // Assert
        assertThat(result.getOrderNumber()).isEqualTo("ORD-ABC123");
        verify(inventoryService).reserveStock(any(), eq(2));
        verify(paymentService).processPayment(any(), eq("tok_valid"));
        verify(eventPublisher).publishOrderCreated(any());
    }

    @Test
    void checkout_shouldReturnExistingOrder_whenIdempotencyKeyMatches() {
        // Arrange — simulate a duplicate request
        Order existingOrder = mock(Order.class);
        when(existingOrder.getId()).thenReturn(1L);
        when(existingOrder.getItems()).thenReturn(java.util.List.of());

        when(orderRepository.findByIdempotencyKey("dup-key"))
            .thenReturn(Optional.of(existingOrder));

        CheckoutRequest request = new CheckoutRequest();
        request.setIdempotencyKey("dup-key");

        // Act
        OrderDTO result = orderService.checkout(99L, request);

        // Assert — should not create new order or charge payment again
        verify(paymentService, never()).processPayment(any(), any());
        verify(inventoryService, never()).reserveStock(any(), anyInt());
        verify(eventPublisher, never()).publishOrderCreated(any());
    }

    @Test
    void updateStatus_shouldFollowStateMachine() {
        Order order = new Order(testCustomer, testAddress,
            BigDecimal.TEN, "key-1");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        // PENDING → CONFIRMED is valid
        orderService.updateStatus(1L, OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void updateStatus_shouldThrow_onInvalidTransition() {
        Order order = new Order(testCustomer, testAddress,
            BigDecimal.TEN, "key-2");
        // Order starts PENDING
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // PENDING → SHIPPED is invalid (must go through CONFIRMED first)
        assertThatThrownBy(() -> orderService.updateStatus(1L, OrderStatus.SHIPPED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid transition");
    }

    @Test
    void getOrder_shouldThrow_whenCustomerDoesNotOwnOrder() {
        Customer otherCustomer = new Customer("Jane", "Smith",
            "jane@example.com", "hash");
        Order order = new Order(otherCustomer, testAddress,
            BigDecimal.TEN, "key-3");

        when(orderRepository.findByIdWithItems(1L))
            .thenReturn(Optional.of(order));

        // Customer ID 999 is not the owner
        assertThatThrownBy(() -> orderService.getOrder(1L, 999L))
            .isInstanceOf(org.springframework.security.access
                .AccessDeniedException.class);
    }

    @Test
    void checkout_shouldReleaseStockAndCancelOrder_whenPaymentFails() {
        // Arrange
        // testProduct.setId(42L);
        // testCart = new Cart(testCustomer);
        // testCart.addItem(testProduct, 2);
        testCustomer.setCart(testCart);

        CheckoutRequest request = new CheckoutRequest();
        request.setShippingAddressId(null); // matches testAddress (unpersisted, id=null)
        request.setIdempotencyKey("pay-fail-key");
        request.setPaymentToken("tok_declined");

        when(orderRepository.findByIdempotencyKey("pay-fail-key"))
            .thenReturn(Optional.empty());
        
        when(customerRepository.findByIdWithCart(1L))
            .thenReturn(Optional.of(testCustomer));
            
        // Return the same Order instance so status changes are visible

        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new RuntimeException("card declined"))
            .when(paymentService)
            .processPayment(any(Order.class), eq("tok_declined"));

        // Act & Assert - checkout fails to the client
        assertThatThrownBy(() -> orderService.checkout(1L, request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Payment failed")
            .hasMessageContaining("card declined");

        // Compensation: stock reserved then released
        verify(inventoryService).reserveStock(42L, 2);
        verify(inventoryService).releaseStock(42L, 2);

        // Order cancelled, not confirmed
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Happy-path side effects must not run
        verify(eventPublisher, never()).publishOrderCreated(any ());
        verify(cartRepository, never()).save(any());
        verify(auditService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
