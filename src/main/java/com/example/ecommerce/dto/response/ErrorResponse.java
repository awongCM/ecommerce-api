package com.example.ecommerce.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        int status,
        String message,
        List<String> errors,
        LocalDateTime timestamp) {

    public ErrorResponse(int status, String message) {
        this(status, message, null, LocalDateTime.now());
    }

    public ErrorResponse(int status, String message, List<String> errors) {
        this(status, message, errors, LocalDateTime.now());
    }
}
