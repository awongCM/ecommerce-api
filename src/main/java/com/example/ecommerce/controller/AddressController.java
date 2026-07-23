package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.AddressRequest;
import com.example.ecommerce.dto.response.AddressDTO;
import com.example.ecommerce.service.AddressService;
import com.example.ecommerce.service.CustomerLookupService;
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
    private final CustomerLookupService customerLookup;

    public AddressController(AddressService addressService,
                             CustomerLookupService customerLookup) {
        this.addressService = addressService;
        this.customerLookup = customerLookup;
    }

    @GetMapping
    public ResponseEntity<List<AddressDTO>> listAddresses(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
            addressService.listAddresses(customerLookup.requireCustomerId(user.getUsername())));
    }

    @PostMapping
    public ResponseEntity<AddressDTO> createAddress(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(201)
            .body(addressService.createAddress(
                customerLookup.requireCustomerId(user.getUsername()), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressDTO> updateAddress(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(
            customerLookup.requireCustomerId(user.getUsername()), id, request));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<AddressDTO> setDefault(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(addressService.setDefault(
            customerLookup.requireCustomerId(user.getUsername()), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id) {
        addressService.deleteAddress(
            customerLookup.requireCustomerId(user.getUsername()), id);
        return ResponseEntity.noContent().build();
    }
}
