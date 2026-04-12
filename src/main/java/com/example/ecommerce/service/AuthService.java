package com.example.ecommerce.service;

import com.example.ecommerce.domain.Cart;
import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.dto.request.LoginRequest;
import com.example.ecommerce.dto.request.RegisterRequest;
import com.example.ecommerce.dto.response.AuthResponse;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtTokenProvider tokenProvider;

    public AuthService(CustomerRepository customerRepository,
                       CartRepository cartRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authManager,
                       JwtTokenProvider tokenProvider) {
        this.customerRepository = customerRepository;
        this.cartRepository = cartRepository;
        this.passwordEncoder = passwordEncoder;
        this.authManager = authManager;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException(
                "Email already registered: " + request.getEmail());
        }

        Customer customer = new Customer(
            request.getFirstName(),
            request.getLastName(),
            request.getEmail(),
            passwordEncoder.encode(request.getPassword())
        );
        customerRepository.save(customer);

        // Auto-create an empty cart for the new customer
        Cart cart = new Cart(customer);
        cartRepository.save(cart);

        // Auto-login after registration
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword()));

        String token = tokenProvider.generateToken(auth);
        return new AuthResponse(token, tokenProvider.getExpiryMs(),
            customer.getEmail(), customer.getFullName());
    }

    public AuthResponse login(LoginRequest request) {
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword()));

        Customer customer = customerRepository.findByEmail(request.getEmail())
            .orElseThrow(() ->
                new ResourceNotFoundException("Customer", null));

        String token = tokenProvider.generateToken(auth);
        return new AuthResponse(token, tokenProvider.getExpiryMs(),
            customer.getEmail(), customer.getFullName());
    }
}
