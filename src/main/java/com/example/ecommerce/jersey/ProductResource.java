package com.example.ecommerce.jersey;

import com.example.ecommerce.dto.request.CreateProductRequest;
import com.example.ecommerce.dto.response.ProductDTO;
import com.example.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.springframework.stereotype.Component;

/**
 * Jersey (JAX-RS) implementation of the Product resource.
 * Compare this side-by-side with ProductController.java (Spring MVC)
 * to see the annotation differences clearly.
 *
 * Spring MVC  →  Jersey (JAX-RS)
 * --------------------------------
 * @RestController → @Path + @Component
 * @GetMapping     → @GET + @Path
 * @PostMapping    → @POST
 * @PathVariable   → @PathParam
 * @RequestParam   → @QueryParam
 * @RequestBody    → (implicit — parameter with @Consumes on method)
 * ResponseEntity  → Response
 */
@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Component
public class ProductResource {

    private final ProductService productService;

    public ProductResource(ProductService productService) {
        this.productService = productService;
    }

    // GET /jersey/products?q=laptop&page=0&size=20
    @GET
    public Response search(
            @QueryParam("q") @DefaultValue("") String query,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return Response.ok(productService.search(query, page, size)).build();
    }

    // GET /jersey/products/42
    @GET
    @Path("/{id}")
    public Response getProduct(@PathParam("id") Long id) {
        ProductDTO product = productService.findById(id);
        return Response.ok(product).build();
    }

    // GET /jersey/products/category/3
    @GET
    @Path("/category/{categoryId}")
    public Response getByCategory(
            @PathParam("categoryId") Long categoryId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return Response.ok(
            productService.findByCategory(categoryId, page, size)).build();
    }

    // POST /jersey/products
    @POST
    public Response createProduct(
            @Valid CreateProductRequest request,
            @Context UriInfo uriInfo) {
        ProductDTO created = productService.createProduct(request);
        URI location = uriInfo.getAbsolutePathBuilder()
            .path(created.getId().toString())
            .build();
        return Response.created(location).entity(created).build();
    }

    // PUT /jersey/products/42
    @PUT
    @Path("/{id}")
    public Response updateProduct(
            @PathParam("id") Long id,
            @Valid CreateProductRequest request) {
        ProductDTO updated = productService.updateProduct(id, request);
        return Response.ok(updated).build();
    }

    // DELETE /jersey/products/42
    @DELETE
    @Path("/{id}")
    public Response deleteProduct(@PathParam("id") Long id) {
        productService.deleteProduct(id);
        return Response.noContent().build();
    }
}
