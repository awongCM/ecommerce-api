package com.example.ecommerce.jersey;

import com.example.ecommerce.dto.request.CheckoutRequest;
import com.example.ecommerce.dto.response.OrderDTO;
import com.example.ecommerce.service.CustomerLookupService;
import com.example.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import org.springframework.stereotype.Component;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Component
public class OrderResource {

    private final OrderService orderService;
    private final CustomerLookupService customerLookup;

    public OrderResource(OrderService orderService,
                         CustomerLookupService customerLookup) {
        this.orderService = orderService;
        this.customerLookup = customerLookup;
    }

    @POST
    @Path("/checkout")
    public Response checkout(
            @Valid CheckoutRequest request,
            @Context SecurityContext securityContext,
            @Context UriInfo uriInfo) {

        String email = securityContext.getUserPrincipal().getName();
        Long customerId = customerLookup.requireCustomerId(email);

        OrderDTO order = orderService.checkout(customerId, request);
        URI location = uriInfo.getAbsolutePathBuilder()
            .path(order.getId().toString())
            .build();
        return Response.created(location).entity(order).build();
    }

    @GET
    @Path("/{id}")
    public Response getOrder(
            @PathParam("id") Long id,
            @Context SecurityContext securityContext) {

        String email = securityContext.getUserPrincipal().getName();
        Long customerId = customerLookup.requireCustomerId(email);
        return Response.ok(orderService.getOrder(id, customerId)).build();
    }
}
