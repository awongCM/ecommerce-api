package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;


@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);
    Page<Category> findByParentIsNull(Pageable pageable);
    Page<Category> findByParentId(Long parentId, Pageable pageable);
    boolean existsByName(String name);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.subcategories WHERE c.id = :id")
    Optional<Category> findByIdWithSubcategories(@Param("id") Long id);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = :id")
    Optional<Category> findByIdWithParent(@Param("id") Long id);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.subcategories WHERE c.parent.id = :parentId")
    List<Category> findByParentIdWithSubcategories(@Param("parentId") Long parentId);
}
