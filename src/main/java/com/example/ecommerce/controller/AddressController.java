package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.AddressRequest;
import com.example.ecommerce.dto.response.AddressDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;
    private final CustomerRepository customerRepository;

    public AddressController(AddressService addressService,
                             CustomerRepository customerRepository) {
        this.addressService     = addressService;
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public ResponseEntity<List<AddressDTO>> listAddresses(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(addressService.listAddresses(resolveId(user)));
    }

    @PostMapping
    public ResponseEntity<AddressDTO> createAddress(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(201)
            .body(addressService.createAddress(resolveId(user), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressDTO> updateAddress(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(resolveId(user), id, request));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<AddressDTO> setDefault(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(addressService.setDefault(resolveId(user), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        addressService.deleteAddress(resolveId(user), id);
        return ResponseEntity.noContent().build();
    }

    private Long resolveId(UserDetails user) {
        return customerRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Customer email: " + user.getUsername()))
            .getId();
    }
}
