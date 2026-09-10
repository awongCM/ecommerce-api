package com.example.ecommerce.dto.response;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String email,
        String fullName) {

    public AuthResponse(String token, long expiresInMs,
                        String email, String fullName) {
        this(token, "Bearer", expiresInMs, email, fullName);
    }
}
