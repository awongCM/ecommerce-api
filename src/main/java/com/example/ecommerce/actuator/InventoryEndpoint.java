package com.example.ecommerce.actuator;

import com.example.ecommerce.dto.response.ProductDTO;
import com.example.ecommerce.service.ProductService;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
@Endpoint(id = "inventory")
public class InventoryEndpoint {

    private final ProductService productService;

    public InventoryEndpoint(ProductService productService) {
        this.productService = productService;
    }

    // GET /actuator/inventory
    @ReadOperation
    public Map<String, Object> getInventoryStatus() {
        List<ProductDTO> lowStock = productService.getLowStockProducts(10);

        return Map.of(
            "lowStockCount", lowStock.size(),
            "lowStockThreshold", 10,
            "lowStockProducts", lowStock.stream()
                .map(p -> Map.of(
                    "id", p.getId(),
                    "name", p.getName(),
                    "sku", p.getSku(),
                    "quantity", p.getStockQuantity()
                ))
                .toList()
        );
    }

    // GET /actuator/inventory/{sku}
    @ReadOperation
    public Map<String, Object> getProductStock(@Selector String sku) {
        // In real impl: query by SKU
        return Map.of("sku", sku, "message", "Query by SKU");
    }
}
