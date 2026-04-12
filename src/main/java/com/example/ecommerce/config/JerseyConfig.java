package com.example.ecommerce.config;

import com.example.ecommerce.jersey.JerseyAuthFilter;
import com.example.ecommerce.jersey.JerseyExceptionMapper;
import com.example.ecommerce.jersey.OrderResource;
import com.example.ecommerce.jersey.ProductResource;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.ws.rs.ApplicationPath;

@Configuration
@ApplicationPath("/jersey")  // All Jersey endpoints live under /jersey/
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        // Register resource classes
        register(ProductResource.class);
        register(OrderResource.class);

        // Register filters and providers
        register(JerseyAuthFilter.class);
        register(JerseyExceptionMapper.class);

        // Enable bean validation error messages
        property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, true);
        property(ServerProperties.WADL_FEATURE_DISABLE, true);
    }
}
