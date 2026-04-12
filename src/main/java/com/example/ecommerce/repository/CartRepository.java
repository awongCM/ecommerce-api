package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items i " +
           "LEFT JOIN FETCH i.product WHERE c.customer.id = :customerId")
    Optional<Cart> findByCustomerIdWithItems(@Param("customerId") Long customerId);
}
