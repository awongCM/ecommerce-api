package com.example.ecommerce.dto.response;

import com.example.ecommerce.domain.enums.Role;
import java.time.LocalDateTime;
import java.util.Set;

public class AdminUserDTO {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Set<Role> roles;
    private LocalDateTime createdAt;

    public AdminUserDTO(Long id, String email, String firstName,
                        String lastName, Set<Role> roles,
                        LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Set<Role> getRoles() { return roles; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
