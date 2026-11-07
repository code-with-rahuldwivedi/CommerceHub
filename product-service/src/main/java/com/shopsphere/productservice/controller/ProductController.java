package com.shopsphere.productservice.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.shopsphere.productservice.dto.ProductQuery;
import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.dto.StatusRequest;
import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.entity.ProductStatus;
import com.shopsphere.productservice.security.CurrentUser;
import com.shopsphere.productservice.service.ProductService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Public catalog search. Customers only see ACTIVE products; admins also see INACTIVE
     * (and any status via the "status" filter). Sort: id, name, price, createdAt, updatedAt.
     */
    @GetMapping
    public PagedModel<ProductResponse> search(
            @RequestParam(name = "q", required = false) @Size(max = 100) String text,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxPrice,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(ProductService.MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) @Size(max = 50) String sort) {

        ProductQuery query = new ProductQuery(text, category, minPrice, maxPrice, status, page, size, sort);
        return new PagedModel<>(productService.search(query, CurrentUser.fromContext()));
    }

    @GetMapping("/batch")
    public List<ProductResponse> batch(
            @RequestParam("ids") @Size(min = 1, max = ProductService.MAX_BATCH_SIZE) List<@Positive Long> ids) {
        return productService.getBatch(ids);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable @Positive long id) {
        return productService.get(id, CurrentUser.fromContext());
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request, CurrentUser.fromContext());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable @Positive long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request, CurrentUser.fromContext());
    }

    @PutMapping("/{id}/status")
    public ProductResponse changeStatus(@PathVariable @Positive long id,
                                        @Valid @RequestBody StatusRequest request) {
        return productService.changeStatus(id, request.status(), CurrentUser.fromContext());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable @Positive long id) {
        productService.archive(id, CurrentUser.fromContext());
        return ResponseEntity.noContent().build();
    }
}
