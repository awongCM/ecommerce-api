package com.example.ecommerce.security;

import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.repository.CustomerRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public UserDetailsServiceImpl(CustomerRepository repo) {
        this.customerRepository = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        Customer customer = customerRepository.findByEmail(email)
            .orElseThrow(() ->
                new UsernameNotFoundException("No user with email: " + email));

        return User.builder()
            .username(customer.getEmail())
            .password(customer.getPasswordHash())
            .authorities(customer.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .collect(Collectors.toList()))
            .build();
    }
}
