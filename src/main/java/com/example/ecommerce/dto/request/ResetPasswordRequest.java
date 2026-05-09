package com.example.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;

    public String getToken()       { return token; }
    public void setToken(String t) { this.token = t; }
    public String getNewPassword()       { return newPassword; }
    public void setNewPassword(String p) { this.newPassword = p; }
}
