package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CategoryRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private CategoryRepository categoryRepository;

    private Category electronics;
    private Category laptops;
    private Category phones;

    @BeforeEach
    void setUp() {
        electronics = new Category("Electronics");
        entityManager.persist(electronics);

        laptops = new Category("Laptops");
        laptops.setParent(electronics);
        entityManager.persist(laptops);

        phones = new Category("Phones");
        phones.setParent(electronics);
        entityManager.persist(phones);

        entityManager.flush();
        entityManager.clear(); // evict from session cache — queries must hit the DB
    }

    // --- findByParentIsNull ---

    @Test
    void findByParentIsNull_list_shouldReturnOnlyTopLevelCategories() {
        List<Category> result = categoryRepository.findByParentIsNull();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Electronics");
    }

    @Test
    void findByParentIsNull_paged_shouldReturnPageOfTopLevelCategories() {
        Page<Category> result = categoryRepository.findByParentIsNull(PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Electronics");
    }

    // --- findByParentId (pageable) ---

    @Test
    void findByParentId_shouldReturnChildCategories() {
        Page<Category> result = categoryRepository.findByParentId(
            electronics.getId(), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
            .extracting(Category::getName)
            .containsExactlyInAnyOrder("Laptops", "Phones");
    }

    // --- existsByName ---

    @Test
    void existsByName_shouldReturnTrue_whenNameExists() {
        assertThat(categoryRepository.existsByName("Electronics")).isTrue();
    }

    @Test
    void existsByName_shouldReturnFalse_whenNameAbsent() {
        assertThat(categoryRepository.existsByName("Furniture")).isFalse();
    }

    // --- findByName ---

    @Test
    void findByName_shouldReturnCategory_whenFound() {
        Optional<Category> result = categoryRepository.findByName("Electronics");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Electronics");
    }

    @Test
    void findByName_shouldReturnEmpty_whenNotFound() {
        assertThat(categoryRepository.findByName("Furniture")).isEmpty();
    }

    // --- findByIdWithSubcategories ---

    @Test
    void findByIdWithSubcategories_shouldReturnCategoryWithChildren() {
        Optional<Category> result = categoryRepository.findByIdWithSubcategories(electronics.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getSubcategories())
            .extracting(Category::getName)
            .containsExactlyInAnyOrder("Laptops", "Phones");
    }

    @Test
    void findByIdWithSubcategories_shouldReturnEmpty_whenNotFound() {
        assertThat(categoryRepository.findByIdWithSubcategories(999L)).isEmpty();
    }
}
