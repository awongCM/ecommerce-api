package com.example.ecommerce.service;

import com.example.ecommerce.domain.Address;
import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.dto.request.AddressRequest;
import com.example.ecommerce.dto.response.AddressDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    public AddressService(AddressRepository addressRepository,
                          CustomerRepository customerRepository) {
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
    }

    public List<AddressDTO> listAddresses(Long customerId) {
        return addressRepository.findByCustomerId(customerId)
            .stream()
            .map(AddressDTO::from)
            .toList();
    }

    @Transactional
    public AddressDTO createAddress(Long customerId, AddressRequest request) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        if (request.isDefault()) {
            addressRepository.clearDefaultForCustomer(customerId);
        }

        Address address = new Address(
            customer,
            request.getStreetLine1(),
            request.getCity(),
            request.getState(),
            request.getPostcode(),
            request.getCountry()
        );
        address.setStreetLine2(request.getStreetLine2());
        address.setDefault(request.isDefault());

        // If this is the customer's very first address, make it the default automatically
        if (!request.isDefault() && addressRepository.findByCustomerId(customerId).isEmpty()) {
            address.setDefault(true);
        }

        return AddressDTO.from(addressRepository.save(address));
    }

    @Transactional
    public AddressDTO updateAddress(Long customerId, Long addressId, AddressRequest request) {
        Address address = findOwned(customerId, addressId);

        if (request.isDefault()) {
            addressRepository.clearDefaultForCustomer(customerId);
        }

        address.setStreetLine1(request.getStreetLine1());
        address.setStreetLine2(request.getStreetLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPostcode(request.getPostcode());
        address.setCountry(request.getCountry());
        address.setDefault(request.isDefault());

        return AddressDTO.from(addressRepository.save(address));
    }

    @Transactional
    public AddressDTO setDefault(Long customerId, Long addressId) {
        addressRepository.clearDefaultForCustomer(customerId);
        Address address = findOwned(customerId, addressId);
        address.setDefault(true);
        return AddressDTO.from(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        Address address = findOwned(customerId, addressId);
        addressRepository.delete(address);
    }

    private Address findOwned(Long customerId, Long addressId) {
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Address not found or does not belong to this customer"));
    }
}
