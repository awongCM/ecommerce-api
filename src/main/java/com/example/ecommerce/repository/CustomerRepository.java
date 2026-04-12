package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.cart cart " +
           "LEFT JOIN FETCH cart.items items " +
           "LEFT JOIN FETCH items.product " +
           "WHERE c.id = :id")
    Optional<Customer> findByIdWithCart(@Param("id") Long id);
}
