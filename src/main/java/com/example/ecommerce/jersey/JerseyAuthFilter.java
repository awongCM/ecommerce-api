package com.example.ecommerce.jersey;

import com.example.ecommerce.security.JwtTokenProvider;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.Provider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Jersey equivalent of JwtAuthenticationFilter.
 * In Spring MVC: OncePerRequestFilter
 * In Jersey:     ContainerRequestFilter annotated with @Provider
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@Component
public class JerseyAuthFilter implements ContainerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JerseyAuthFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Skip auth for GET requests (public browsing)
        if (requestContext.getMethod().equals("GET")) return;

        String header = requestContext.getHeaderString("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            abort(requestContext, Response.Status.UNAUTHORIZED,
                "Missing authorization token");
            return;
        }

        String token = header.substring(7);
        if (!tokenProvider.validateToken(token)) {
            abort(requestContext, Response.Status.UNAUTHORIZED,
                "Invalid or expired token");
            return;
        }

        String email = tokenProvider.getEmail(token);
        List<String> roles = tokenProvider.getRoles(token);

        // Set Spring Security context so @PreAuthorize works
        var authorities = roles.stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
            .collect(Collectors.toList());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, null, authorities)
        );

        // Set JAX-RS SecurityContext so resource can call
        // securityContext.getUserPrincipal()
        requestContext.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() { return () -> email; }

            @Override
            public boolean isUserInRole(String role) {
                return roles.contains(role);
            }

            @Override public boolean isSecure() {
                return requestContext.getUriInfo()
                    .getRequestUri().getScheme().equals("https");
            }
            @Override public String getAuthenticationScheme() {
                return "Bearer";
            }
        });
    }

    private void abort(ContainerRequestContext ctx,
                       Response.Status status, String message) {
        ctx.abortWith(Response.status(status)
            .entity("{\"error\":\"" + message + "\"}")
            .type(MediaType.APPLICATION_JSON)
            .build());
    }
}
