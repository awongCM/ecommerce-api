package com.example.ecommerce.service;

import com.example.ecommerce.domain.Address;
import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.dto.request.AddressRequest;
import com.example.ecommerce.dto.response.AddressDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock private AddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;

    @InjectMocks
    private AddressService addressService;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer("John", "Doe",
            "john@example.com", "hashedPassword");
    }

    @Test
    void createAddress_shouldCreateAddressSuccessfully() {
        // Arrange
        AddressRequest request = new AddressRequest();
        request.setStreetLine1("123 Main St");
        request.setCity("Sydney");
        request.setState("NSW");
        request.setPostcode("2000");
        request.setCountry("AU");
        request.setDefault(true);

        Address saved = new Address(testCustomer, "123 Main St", "Sydney", "NSW", "2000", "AU");
        saved.setDefault(true);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        // findByCustomerId is NOT called when isDefault=true (short-circuit in service)
        when(addressRepository.save(any(Address.class))).thenReturn(saved);

        // Act
        AddressDTO result = addressService.createAddress(1L, request);

        // Assert
        assertThat(result.getStreetLine1()).isEqualTo("123 Main St");
        assertThat(result.getCity()).isEqualTo("Sydney");
        assertThat(result.getState()).isEqualTo("NSW");
        assertThat(result.getPostcode()).isEqualTo("2000");
        assertThat(result.getCountry()).isEqualTo("AU");
        assertThat(result.isDefault()).isTrue();

        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void createAddress_shouldCreateAddressSuccessfully_whenIsDefaultIsFalse() {
        // Arrange
        AddressRequest request = new AddressRequest();
        request.setStreetLine1("123 Main St");
        request.setCity("Sydney");
        request.setState("NSW");
        request.setPostcode("2000");
        request.setCountry("AU");
        request.setDefault(false);

        Address saved = new Address(testCustomer, "123 Main St", "Sydney", "NSW", "2000", "AU");
        saved.setDefault(false);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        // findByCustomerId is NOT called when isDefault=false (short-circuit in service)
        when(addressRepository.save(any(Address.class))).thenReturn(saved);

        // Act
        AddressDTO result = addressService.createAddress(1L, request);

        // Assert
        assertThat(result.getStreetLine1()).isEqualTo("123 Main St");
        assertThat(result.getCity()).isEqualTo("Sydney");
        assertThat(result.getState()).isEqualTo("NSW");
        assertThat(result.getPostcode()).isEqualTo("2000");
        assertThat(result.getCountry()).isEqualTo("AU");
        assertThat(result.isDefault()).isFalse();

        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void createAddress_shouldThrow_whenCustomerNotFound() {
        // Arrange
        AddressRequest request = new AddressRequest();
        request.setStreetLine1("123 Main St");
        request.setCity("Sydney");
        request.setState("NSW");
        request.setPostcode("2000");
        request.setCountry("AU");
        request.setDefault(true);

        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        // Act
        assertThatThrownBy(() -> addressService.createAddress(1L, request))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAddresses_shouldReturnAllAddressesForCustomer() {
        // Arrange
        Address a1 = new Address(testCustomer, "123 Main St", "Sydney", "NSW", "2000", "AU");
        Address a2 = new Address(testCustomer, "246 Main St", "Sydney", "NSW", "2000", "AU");
        when(addressRepository.findByCustomerId(1L)).thenReturn(List.of(a1, a2));

        // Act
        List<AddressDTO> result = addressService.listAddresses(1L);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStreetLine1()).isEqualTo("123 Main St");
        assertThat(result.get(0).getCity()).isEqualTo("Sydney");
        assertThat(result.get(0).getState()).isEqualTo("NSW");
        assertThat(result.get(0).getPostcode()).isEqualTo("2000");
        assertThat(result.get(0).getCountry()).isEqualTo("AU");
        assertThat(result.get(0).isDefault()).isFalse();

        assertThat(result.get(1).getStreetLine1()).isEqualTo("246 Main St");
        assertThat(result.get(1).getCity()).isEqualTo("Sydney");
        assertThat(result.get(1).getState()).isEqualTo("NSW");
        assertThat(result.get(1).getPostcode()).isEqualTo("2000");
        assertThat(result.get(1).getCountry()).isEqualTo("AU");
        assertThat(result.get(1).isDefault()).isFalse();

        verify(addressRepository).findByCustomerId(1L);
    }

    @Test
    void deleteAddress_shouldDeleteAddressSuccessfully() {
        // Arrange
        Address address = new Address(testCustomer, "123 Main St", "Sydney", "NSW", "2000", "AU");
        when(addressRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(address));

        // Act
        addressService.deleteAddress(1L, 1L);

        // Assert
        verify(addressRepository).delete(address);
    }

    @Test
    void deleteAddress_shouldThrow_whenAddressNotFound() {
        // Arrange
        when(addressRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.empty());

        // Act
        assertThatThrownBy(() -> addressService.deleteAddress(1L, 1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteAddress_shouldThrow_whenAddressNotOwnedByCustomer() {
        // Arrange
        when(addressRepository.findByIdAndCustomerId(2L, 1L)).thenReturn(Optional.empty());

        // Act
        assertThatThrownBy(() -> addressService.deleteAddress(1L, 2L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

}
