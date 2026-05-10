package com.example.ecommerce.repository;

import com.example.ecommerce.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest  // Only loads JPA layer, uses H2 automatically
class AddressRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AddressRepository addressRepository;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer("John", "Doe", "john@example.com", "hashedPassword");
        entityManager.persist(customer);
    }

    @Test
    void findByCustomerId_shouldReturnAddressesForCustomer() {
        Address address = new Address(customer, "123 Main St", "Sydney", "NSW", "2000", "AU");
        entityManager.persist(address);
        entityManager.flush();

        List<Address> result = addressRepository.findByCustomerId(customer.getId());
        assertThat(result).hasSize(1);
    }

    @Test
    void findByIdAndCustomerId_shouldReturnAddressForCustomer() {
        Address address = new Address(customer, "123 Main St", "Sydney", "NSW", "2000", "AU");
        entityManager.persist(address);
        entityManager.flush();

        Optional<Address> result = addressRepository.findByIdAndCustomerId(address.getId(), customer.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getStreetLine1()).isEqualTo("123 Main St");
    }

    @Test
    void clearDefaultForCustomer_shouldClearDefaultForCustomer() {
        Address address = new Address(customer, "123 Main St", "Sydney", "NSW", "2000", "AU");
        entityManager.persist(address);
        Address address2 = new Address(customer, "246 Main St", "Sydney", "NSW", "2000", "AU");
        address2.setDefault(true);
        entityManager.persist(address2);
        entityManager.flush();

        addressRepository.clearDefaultForCustomer(customer.getId());
        assertThat(address.isDefault()).isFalse();
        assertThat(address2.isDefault()).isTrue();
    }

    @Test
    void delete_shouldDeleteAddress() {
        Address address = new Address(customer, "123 Main St", "Sydney", "NSW", "2000", "AU");
        entityManager.persist(address);
        entityManager.flush();

        addressRepository.delete(address);
        assertThat(addressRepository.findById(address.getId())).isEmpty();
        assertThat(addressRepository.findByCustomerId(customer.getId())).isEmpty();
    }

   
}
