package com.shopsphere.productservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.service.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Add Product
    @PostMapping("/createProduct")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse addProduct(@Valid @RequestBody ProductRequest request) {
        return productService.addProduct(request);
    }

    // Get All Products
    @GetMapping("/getAllProducts")
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    // Get Product By Id
    @GetMapping("/product/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    // Update Product
    @PutMapping("/product/{id}")
    public ProductResponse updateProduct(@PathVariable Long id,
                                         @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    // Delete Product
    @DeleteMapping("/product/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }
}