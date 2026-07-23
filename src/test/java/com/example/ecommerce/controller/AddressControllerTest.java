package com.example.ecommerce.controller;

import com.example.ecommerce.config.SecurityConfig;
import com.example.ecommerce.domain.Address;
import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.dto.request.AddressRequest;
import com.example.ecommerce.dto.response.AddressDTO;
import com.example.ecommerce.security.JwtTokenProvider;
import com.example.ecommerce.security.UserDetailsServiceImpl;
import com.example.ecommerce.service.AddressService;
import com.example.ecommerce.service.CustomerLookupService;
import org.springframework.http.MediaType;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AddressController.class)
@Import(SecurityConfig.class)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddressService addressService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private CustomerLookupService customerLookup;

    @Test
    @WithMockUser(username = "john@example.com", roles = "CUSTOMER")
    void listAddresses_shouldReturn200_withAddressList() throws Exception {
        Customer customer = new Customer("John", "Doe", "john@example.com", "hashedPassword");
        Address address = new Address(customer, "123 Main St", "Sydney", "NSW", "2000", "AU");

        // Controller resolves customerId from email via CustomerLookupService
        when(customerLookup.requireCustomerId("john@example.com")).thenReturn(1L);
        when(addressService.listAddresses(1L))
            .thenReturn(List.of(AddressDTO.from(address)));

        mockMvc.perform(get("/api/v1/addresses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].streetLine1").value("123 Main St"))
            .andExpect(jsonPath("$[0].city").value("Sydney"));
    }

    @Test
    void listAddresses_shouldReturn403_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "CUSTOMER")
    void createAddress_shouldReturn201_withAddress() throws Exception {
        Customer customer = new Customer("John", "Doe", "john@example.com", "hashedPassword");
        Address address = new Address(customer, "123 Main St", "Sydney", "NSW", "2000", "AU");
        when(customerLookup.requireCustomerId("john@example.com")).thenReturn(1L);
        when(addressService.createAddress(eq(1L), any(AddressRequest.class))).thenReturn(AddressDTO.from(address));
        
        mockMvc.perform(post("/api/v1/addresses")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "streetLine1": "123 Main St",
                    "city": "Sydney",
                    "state": "NSW",
                    "postcode": "2000",
                    "country": "AU",
                    "isDefault": false
                }
            """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.streetLine1").value("123 Main St"))
            .andExpect(jsonPath("$.city").value("Sydney"))
            .andExpect(jsonPath("$.state").value("NSW"))
            .andExpect(jsonPath("$.postcode").value("2000"))
            .andExpect(jsonPath("$.country").value("AU"));
    }
}
