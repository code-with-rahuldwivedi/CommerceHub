package com.shopsphere.productservice.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.repository.ProductRepository;

@Service
public class ProductServiceImpl implements ProductService{
	
	@Autowired
	private ProductRepository productRepository;

	private Product mapToEntity(ProductRequest request) {

	    Product product = new Product();

	    product.setName(request.getName());
	    product.setDescription(request.getDescription());
	    product.setPrice(request.getPrice());
	    product.setStock(request.getStock());
	    product.setCategory(request.getCategory());
	    product.setImageUrl(request.getImageUrl());

	    return product;
	}
	
	private ProductResponse mapToResponse(Product product) {

	    ProductResponse response = new ProductResponse();

	    response.setId(product.getId());
	    response.setName(product.getName());
	    response.setDescription(product.getDescription());
	    response.setPrice(product.getPrice());
	    response.setStock(product.getStock());
	    response.setCategory(product.getCategory());
	    response.setImageUrl(product.getImageUrl());
	    response.setCreatedAt(product.getCreatedAt());
	    response.setUpdatedAt(product.getUpdatedAt());

	    return response;
	}
	
	@Override
	public ProductResponse addProduct(ProductRequest request) {

	    Product product = mapToEntity(request);

	    Product saved = productRepository.save(product);

	    return mapToResponse(saved);
	}
	
	@Override
	public List<ProductResponse> getAllProducts() {

	    return productRepository.findAll()
	            .stream()
	            .map(this::mapToResponse)
	            .toList();
	}
	
	@Override
	public ProductResponse getProductById(Long id) {

	    Product product = productRepository.findById(id)
	            .orElseThrow(() -> new RuntimeException("Product not found"));

	    return mapToResponse(product);
	}
	
	@Override
	public ProductResponse updateProduct(Long id, ProductRequest request) {

	    Product product = productRepository.findById(id)
	            .orElseThrow(() -> new RuntimeException("Product not found"));

	    product.setName(request.getName());
	    product.setDescription(request.getDescription());
	    product.setPrice(request.getPrice());
	    product.setStock(request.getStock());
	    product.setCategory(request.getCategory());
	    product.setImageUrl(request.getImageUrl());

	    Product updated = productRepository.save(product);

	    return mapToResponse(updated);
	}
	
	@Override
	public void deleteProduct(Long id) {

	    Product product = productRepository.findById(id)
	            .orElseThrow(() -> new RuntimeException("Product not found"));

	    productRepository.delete(product);
	}
}

