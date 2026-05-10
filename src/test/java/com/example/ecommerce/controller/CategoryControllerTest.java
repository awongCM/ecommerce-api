package com.example.ecommerce.controller;

import com.example.ecommerce.config.SecurityConfig;
import com.example.ecommerce.domain.Category;
import com.example.ecommerce.dto.response.CategoryDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.security.JwtTokenProvider;
import com.example.ecommerce.security.UserDetailsServiceImpl;
import com.example.ecommerce.service.CategoryService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CategoryService categoryService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    // --- GET /api/v1/categories ---

    @Test
    void listTopLevel_shouldReturn200_withoutAuth() throws Exception {
        // Arrange
        CategoryDTO dto = CategoryDTO.from(new Category("Electronics"));
        when(categoryService.listTopLevel(0, 20))
            .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        // Act + Assert
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("Electronics"));
    }

    // --- GET /api/v1/categories/{id} ---

    @Test
    void getCategory_shouldReturn200_whenFound() throws Exception {
        // Arrange
        when(categoryService.getById(1L))
            .thenReturn(CategoryDTO.from(new Category("Electronics")));

        // Act + Assert
        mockMvc.perform(get("/api/v1/categories/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    void getCategory_shouldReturn404_whenNotFound() throws Exception {
        // Arrange
        when(categoryService.getById(99L))
            .thenThrow(new ResourceNotFoundException("Category", 99L));

        // Act + Assert
        mockMvc.perform(get("/api/v1/categories/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // --- GET /api/v1/categories/{id}/subcategories ---

    @Test
    void listSubcategories_shouldReturn200() throws Exception {
        // Arrange
        CategoryDTO dto = CategoryDTO.from(new Category("Laptops"));
        when(categoryService.listSubcategories(1L, 0, 20))
            .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        // Act + Assert
        mockMvc.perform(get("/api/v1/categories/1/subcategories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("Laptops"));
    }

    // --- POST /api/v1/categories ---

    @Test
    @WithMockUser(roles = "SELLER")
    void createCategory_shouldReturn201_asSeller() throws Exception {
        // Arrange
        when(categoryService.createCategory(any()))
            .thenReturn(CategoryDTO.from(new Category("Books")));

        // Act + Assert
        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Books","description":"All books"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Books"))
            .andExpect(header().exists("Location"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void createCategory_shouldReturn403_asCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Books"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void createCategory_shouldReturn403_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Books"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SELLER")
    void createCategory_shouldReturn400_whenNameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray());
    }

    // --- PUT /api/v1/categories/{id} ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCategory_shouldReturn200_asAdmin() throws Exception {
        // Arrange
        when(categoryService.updateCategory(eq(1L), any()))
            .thenReturn(CategoryDTO.from(new Category("Consumer Electronics")));

        // Act + Assert
        mockMvc.perform(put("/api/v1/categories/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Consumer Electronics"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Consumer Electronics"));
    }

    // --- DELETE /api/v1/categories/{id} ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCategory_shouldReturn204_asAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void deleteCategory_shouldReturn403_asCustomer() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/1"))
            .andExpect(status().isForbidden());
    }
}
