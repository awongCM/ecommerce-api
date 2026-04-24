package com.example.ecommerce.dto.request;

import com.example.ecommerce.domain.enums.Role;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public class UpdateRolesRequest {

    @NotEmpty(message = "At least one role must be specified")
    private Set<Role> roles;

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
}
