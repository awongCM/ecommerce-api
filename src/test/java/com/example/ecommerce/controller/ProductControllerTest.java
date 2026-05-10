package com.example.ecommerce.controller;

import com.example.ecommerce.config.SecurityConfig;
import com.example.ecommerce.dto.response.ProductDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.security.JwtTokenProvider;
import com.example.ecommerce.security.UserDetailsServiceImpl;
import com.example.ecommerce.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired  
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void search_shouldReturn200_withProductList() throws Exception {
        ProductDTO product = buildProductDTO(1L, "Laptop", new BigDecimal("999.99"));
        Page<ProductDTO> page = new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1);
        when(productService.search(anyString(), anyInt(), anyInt()))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/products")
                .param("q", "laptop")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("Laptop"))
            .andExpect(jsonPath("$.content[0].price").value(999.99));
    }

    @Test
    void getProduct_shouldReturn404_whenNotFound() throws Exception {
        when(productService.findById(99L))
            .thenThrow(new ResourceNotFoundException("Product", 99L));

        mockMvc.perform(get("/api/v1/products/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void createProduct_shouldReturn201_whenSeller() throws Exception {
        String body = """
            {
              "name": "Wireless Mouse",
              "description": "Ergonomic mouse",
              "price": 49.99,
              "stockQuantity": 100,
              "categoryId": 1,
              "sku": "MOUSE-001"
            }
            """;

        ProductDTO created = buildProductDTO(5L, "Wireless Mouse",
            new BigDecimal("49.99"));
        when(productService.createProduct(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Wireless Mouse"))
            .andExpect(header().exists("Location"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void createProduct_shouldReturn403_whenCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_shouldReturn400_whenInvalidRequest() throws Exception {
        String invalidBody = """
            {
              "name": "",
              "price": -10,
              "stockQuantity": -5
            }
            """;

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody)
                .with(org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.user("seller@test.com")
                    .roles("SELLER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray());
    }

    private ProductDTO buildProductDTO(Long id, String name, BigDecimal price) {
        return new ProductDTO(id, name, price);
    }
}
