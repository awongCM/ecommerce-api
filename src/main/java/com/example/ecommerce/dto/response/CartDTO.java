package com.example.ecommerce.dto.response;

import com.example.ecommerce.domain.Cart;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class CartDTO {
    private Long cartId;
    private List<CartItemDTO> items;
    private BigDecimal totalPrice;
    private int totalItems;

    public static CartDTO from(Cart cart) {
        System.out.println("CartDTO from" + cart.getId());
        CartDTO dto = new CartDTO();
        dto.cartId = cart.getId();
        dto.items = cart.getItems().stream()
            .map(CartItemDTO::from)
            .collect(Collectors.toList());
        dto.totalPrice = cart.getTotalPrice();
        dto.totalItems = cart.getTotalItems();
        return dto;
    }

    public Long getCartId() { return cartId; }
    public List<CartItemDTO> getItems() { return items; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public int getTotalItems() { return totalItems; }

    public static class CartItemDTO {
        private Long productId;
        private String productName;
        private BigDecimal unitPrice;
        private int quantity;
        private BigDecimal subtotal;

        public static CartItemDTO from(com.example.ecommerce.domain.CartItem item) {
            CartItemDTO dto = new CartItemDTO();
            dto.productId = item.getProduct().getId();
            dto.productName = item.getProduct().getName();
            dto.unitPrice = item.getProduct().getPrice();
            dto.quantity = item.getQuantity();
            dto.subtotal = item.getSubtotal();
            return dto;
        }

        public Long getProductId() { return productId; }
        public String getProductName() { return productName; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public int getQuantity() { return quantity; }
        public BigDecimal getSubtotal() { return subtotal; }
    }
}
