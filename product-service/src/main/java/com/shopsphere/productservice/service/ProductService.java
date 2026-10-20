package com.shopsphere.productservice.service;

import java.util.List;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;

public interface ProductService {

    ProductResponse addProduct(ProductRequest request);

    List<ProductResponse> getAllProducts();

    ProductResponse getProductById(Long id);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);


	ProductResponse reduceStock(
	        Long productId,
	        Integer quantity,
	        String operationKey
	);

	void restoreStock(
	        Long productId,
	        Integer quantity,
	        String operationKey
	);
}