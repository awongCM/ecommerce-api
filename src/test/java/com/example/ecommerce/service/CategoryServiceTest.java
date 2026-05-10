package com.example.ecommerce.service;

import com.example.ecommerce.domain.Category;
import com.example.ecommerce.dto.request.CreateCategoryRequest;
import com.example.ecommerce.dto.response.CategoryDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;

    @InjectMocks private CategoryService categoryService;

    private Category electronics;
    private Category laptops;

    @BeforeEach
    void setUp() {
        electronics = new Category("Electronics");
        laptops = new Category("Laptops");
        laptops.setParent(electronics);
    }

    // --- listTopLevel ---

    @Test
    void listTopLevel_shouldReturnPageOfTopLevelCategories() {
        // Arrange
        Page<Category> page = new PageImpl<>(List.of(electronics));
        when(categoryRepository.findByParentIsNull(any(Pageable.class))).thenReturn(page);

        // Act
        Page<CategoryDTO> result = categoryService.listTopLevel(0, 20);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Electronics");
    }

    // --- listSubcategories ---

    @Test
    void listSubcategories_shouldReturnPageOfChildCategories() {
        // Arrange
        Page<Category> page = new PageImpl<>(List.of(laptops));
        when(categoryRepository.findByParentId(eq(1L), any(Pageable.class))).thenReturn(page);

        // Act
        Page<CategoryDTO> result = categoryService.listSubcategories(1L, 0, 20);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Laptops");
    }

    // --- getById ---

    @Test
    void getById_shouldReturnCategory_whenFound() {
        // Arrange
        when(categoryRepository.findByIdWithSubcategories(1L)).thenReturn(Optional.of(electronics));

        // Act
        CategoryDTO result = categoryService.getById(1L);

        // Assert
        assertThat(result.getName()).isEqualTo("Electronics");
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(categoryRepository.findByIdWithSubcategories(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- createCategory ---

    @Test
    void createCategory_shouldCreate_whenNameIsUnique() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Books");
        request.setDescription("All books");
        when(categoryRepository.existsByName("Books")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(new Category("Books"));

        // Act
        CategoryDTO result = categoryService.createCategory(request);

        // Assert
        assertThat(result.getName()).isEqualTo("Books");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void createCategory_shouldThrow_whenNameAlreadyExists() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Electronics");
        when(categoryRepository.existsByName("Electronics")).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> categoryService.createCategory(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Electronics");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategory_shouldSetParent_whenParentIdProvided() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Laptops");
        request.setParentId(1L);
        when(categoryRepository.existsByName("Laptops")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CategoryDTO result = categoryService.createCategory(request);

        // Assert
        assertThat(result.getParentName()).isEqualTo("Electronics");
    }

    @Test
    void createCategory_shouldThrow_whenParentNotFound() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Laptops");
        request.setParentId(99L);
        when(categoryRepository.existsByName("Laptops")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> categoryService.createCategory(request))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- updateCategory ---

    @Test
    void updateCategory_shouldUpdate_whenCategoryExists() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Consumer Electronics");
        request.setDescription("Updated description");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CategoryDTO result = categoryService.updateCategory(1L, request);

        // Assert
        assertThat(result.getName()).isEqualTo("Consumer Electronics");
        assertThat(result.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void updateCategory_shouldThrow_whenCategoryNotFound() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Anything");
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> categoryService.updateCategory(99L, request))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCategory_shouldClearParent_whenParentIdIsNull() {
        // Arrange — parentId deliberately null to trigger the clear-parent branch
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("Laptops");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(laptops));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CategoryDTO result = categoryService.updateCategory(1L, request);

        // Assert
        assertThat(result.getParentName()).isNull();
    }

    // --- deleteCategory ---

    @Test
    void deleteCategory_shouldDelete_whenCategoryExists() {
        // Arrange
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));

        // Act
        categoryService.deleteCategory(1L);

        // Assert
        verify(categoryRepository).delete(electronics);
    }

    @Test
    void deleteCategory_shouldThrow_whenCategoryNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(99L))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).delete(any());
    }
}
