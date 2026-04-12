package com.example.ecommerce.jersey;

import com.example.ecommerce.dto.request.CheckoutRequest;
import com.example.ecommerce.dto.response.OrderDTO;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.springframework.stereotype.Component;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Component
public class OrderResource {

    private final OrderService orderService;
    private final CustomerRepository customerRepo;

    public OrderResource(OrderService orderService,
                         CustomerRepository customerRepo) {
        this.orderService = orderService;
        this.customerRepo = customerRepo;
    }

    @POST
    @Path("/checkout")
    public Response checkout(
            @Valid CheckoutRequest request,
            @Context SecurityContext securityContext,
            @Context UriInfo uriInfo) {

        String email = securityContext.getUserPrincipal().getName();
        Long customerId = resolveCustomerId(email);

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
        Long customerId = resolveCustomerId(email);
        return Response.ok(orderService.getOrder(id, customerId)).build();
    }

    private Long resolveCustomerId(String email) {
        return customerRepo.findByEmail(email)
            .orElseThrow(() ->
                new NotFoundException("Customer not found: " + email))
            .getId();
    }
}
