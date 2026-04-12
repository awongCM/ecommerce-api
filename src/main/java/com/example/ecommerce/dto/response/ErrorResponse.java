package com.example.ecommerce.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class ErrorResponse {
    private int status;
    private String message;
    private List<String> errors;
    private LocalDateTime timestamp = LocalDateTime.now();

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public ErrorResponse(int status, String message, List<String> errors) {
        this(status, message);
        this.errors = errors;
    }

    public int getStatus() { return status; }
    public String getMessage() { return message; }
    public List<String> getErrors() { return errors; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
