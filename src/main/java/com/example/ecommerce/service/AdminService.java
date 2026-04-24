package com.example.ecommerce.service;

import com.example.ecommerce.domain.Customer;
import com.example.ecommerce.domain.enums.Role;
import com.example.ecommerce.dto.response.AdminUserDTO;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class AdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final AuditService auditService;

    public AdminService(CustomerRepository customerRepository,
                        AuditService auditService) {
        this.customerRepository = customerRepository;
        this.auditService = auditService;
    }

    public Page<AdminUserDTO> listUsers(int page, int size) {
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(
            page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return customerRepository.findAll(pageRequest).map(this::toDTO);
    }

    @Transactional
    public AdminUserDTO updateUserRoles(Long userId, Set<Role> newRoles,
                                        String actorEmail) {
        Customer target = customerRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", userId));

        // Prevent admin from removing their own ADMIN role
        if (target.getEmail().equals(actorEmail)
                && !newRoles.contains(Role.ADMIN)
                && target.getRoles().contains(Role.ADMIN)) {
            throw new IllegalStateException(
                "Admins cannot remove their own ADMIN role");
        }

        // Prevent removing the last admin
        boolean removingAdmin = target.getRoles().contains(Role.ADMIN)
            && !newRoles.contains(Role.ADMIN);
        if (removingAdmin && customerRepository.countByRole(Role.ADMIN) <= 1) {
            throw new IllegalStateException(
                "Cannot remove ADMIN role: at least one admin must exist");
        }

        Set<Role> oldRoles = Set.copyOf(target.getRoles());
        target.setRoles(newRoles);
        customerRepository.save(target);

        auditService.log("Customer", String.valueOf(userId),
            "ROLES_UPDATED", oldRoles.toString(), newRoles.toString());

        return toDTO(target);
    }

    private AdminUserDTO toDTO(Customer c) {
        return new AdminUserDTO(
            c.getId(), c.getEmail(),
            c.getFirstName(), c.getLastName(),
            c.getRoles(), c.getCreatedAt());
    }
}
