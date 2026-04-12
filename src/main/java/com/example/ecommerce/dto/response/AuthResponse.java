package com.example.ecommerce.dto.response;

public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresInMs;
    private String email;
    private String fullName;

    public AuthResponse(String token, long expiresInMs,
                        String email, String fullName) {
        this.token = token;
        this.expiresInMs = expiresInMs;
        this.email = email;
        this.fullName = fullName;
    }

    public String getToken() { return token; }
    public String getTokenType() { return tokenType; }
    public long getExpiresInMs() { return expiresInMs; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
}
