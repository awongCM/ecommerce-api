package com.example.ecommerce.jersey;

import com.example.ecommerce.dto.response.ErrorResponse;
import com.example.ecommerce.exception.InsufficientStockException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import org.springframework.stereotype.Component;

/**
 * Jersey equivalent of @RestControllerAdvice / @ExceptionHandler.
 * Implement ExceptionMapper<T> for each exception type.
 */
@Provider
@Component
public class JerseyExceptionMapper
        implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof ResourceNotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(404, exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        if (exception instanceof InsufficientStockException) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(409, exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        if (exception instanceof IllegalStateException) {
            return Response.status(422)
                .entity(new ErrorResponse(422, exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        // Never leak internals
        return Response.serverError()
            .entity(new ErrorResponse(500, "An unexpected error occurred"))
            .type(MediaType.APPLICATION_JSON)
            .build();
    }
}
