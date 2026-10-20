package com.shopsphere.productservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.dto.StockUpdateRequest;
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


	@PostMapping("/product/{id}/reduce-stock")
	public ProductResponse reduceStock(
	        @PathVariable Long id,
	        @RequestHeader("Idempotency-Key") String operationKey,
	        @Valid @RequestBody StockUpdateRequest request) {

	    return productService.reduceStock(
	            id,
	            request.getQuantity(),
	            operationKey
	    );
	}

	@PostMapping("/product/{id}/restore-stock")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restoreStock(
	        @PathVariable Long id,
	        @RequestHeader("Idempotency-Key") String operationKey,
	        @Valid @RequestBody StockUpdateRequest request) {

	    productService.restoreStock(
	            id,
	            request.getQuantity(),
	            operationKey
	    );
	}

}