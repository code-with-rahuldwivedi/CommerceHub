package com.shopsphere.productservice.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.security.CurrentUser;
import com.shopsphere.productservice.service.ProductService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * The original RPC-style routes, kept so existing gateway routes and clients keep working.
 * They are deprecated aliases of {@link ProductController} (responses carry a "Deprecation"
 * header). Remove this class once nothing calls them any more.
 */
@RestController
@RequestMapping("/api")
public class LegacyProductController {

    private final ProductService productService;

    public LegacyProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/createProduct")
    public ResponseEntity<ProductResponse> addProduct(@Valid @RequestBody ProductRequest request,
                                                      HttpServletResponse response) {
        deprecated(response);
        ProductResponse created = productService.create(request, CurrentUser.fromContext());
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    /** At most {@value ProductService#LEGACY_LIST_LIMIT} products; use GET /api/products (paged) instead. */
    @GetMapping("/getAllProducts")
    public List<ProductResponse> getAllProducts(HttpServletResponse response) {
        deprecated(response);
        return productService.legacyList(CurrentUser.fromContext());
    }

    @GetMapping("/product/{id}")
    public ProductResponse getProductById(@PathVariable @Positive long id, HttpServletResponse response) {
        deprecated(response);
        return productService.get(id, CurrentUser.fromContext());
    }

    @PutMapping("/product/{id}")
    public ProductResponse updateProduct(@PathVariable @Positive long id,
                                         @Valid @RequestBody ProductRequest request,
                                         HttpServletResponse response) {
        deprecated(response);
        return productService.update(id, request, CurrentUser.fromContext());
    }

    @DeleteMapping("/product/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable @Positive long id, HttpServletResponse response) {
        deprecated(response);
        productService.archive(id, CurrentUser.fromContext());
        return ResponseEntity.noContent().build();
    }

    private static void deprecated(HttpServletResponse response) {
        response.setHeader("Deprecation", "true");
        response.setHeader("Link", "</api/products>; rel=\"successor-version\"");
    }
}
