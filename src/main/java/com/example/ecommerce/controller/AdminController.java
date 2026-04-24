package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.UpdateRolesRequest;
import com.example.ecommerce.dto.response.AdminUserDTO;
import com.example.ecommerce.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserDTO>> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(adminService.listUsers(page, size));
    }

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<AdminUserDTO> updateRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRolesRequest request,
            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(
            adminService.updateUserRoles(id, request.getRoles(), actor.getUsername()));
    }
}
