package com.shopsphere.productservice.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.entity.OperationType;
import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.entity.StockOperation;
import com.shopsphere.productservice.repository.ProductRepository;
import com.shopsphere.productservice.repository.StockOperationRepository;

@Service
public class ProductServiceImpl implements ProductService{


	private ProductRepository productRepository;

	private final StockOperationRepository stockOperationRepository;

	public ProductServiceImpl(
	        ProductRepository productRepository,
	        StockOperationRepository stockOperationRepository) {
	    this.productRepository = productRepository;
	    this.stockOperationRepository = stockOperationRepository;
	}

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


	@Override
	@Transactional
	public ProductResponse reduceStock(
	        Long productId,
	        Integer quantity,
	        String operationKey) {

	    validateStockOperation(productId, quantity, operationKey);

	    var existing = stockOperationRepository
	            .findByOperationKey(operationKey);

	    if (existing.isPresent()) {
	        StockOperation op = existing.get();
	        validateSameOperation(
	                op, productId, quantity, OperationType.REDUCE);
	        return getProductById(productId);
	    }

	    int updated = productRepository.reduceStock(productId, quantity);

	    if (updated == 0) {
	        if (!productRepository.existsById(productId)) {
	            throw new ResponseStatusException(
	                    HttpStatus.NOT_FOUND, "Product not found");
	        }

	        throw new ResponseStatusException(
	                HttpStatus.CONFLICT, "Insufficient stock");
	    }

	    stockOperationRepository.saveAndFlush(
	            new StockOperation(
	                    operationKey, productId, quantity, OperationType.REDUCE));

	    return getProductById(productId);
	}

	@Override
	@Transactional
	public void restoreStock(
	        Long productId,
	        Integer quantity,
	        String operationKey) {

	    validateStockOperation(productId, quantity, operationKey);

	    var existing = stockOperationRepository
	            .findByOperationKey(operationKey);

	    if (existing.isPresent()) {
	        StockOperation op = existing.get();
	        validateSameOperation(
	                op, productId, quantity, OperationType.RESTORE);
	        return;
	    }

	    int updated = productRepository.restoreStock(productId, quantity);

	    if (updated == 0) {
	        throw new ResponseStatusException(
	                HttpStatus.NOT_FOUND, "Product not found: " + productId);
	    }

	    stockOperationRepository.saveAndFlush(
	            new StockOperation(
	                    operationKey, productId, quantity, OperationType.RESTORE));
	}

	private void validateStockOperation(
	        Long productId, Integer quantity, String operationKey) {

	    if (productId == null || productId <= 0) {
	        throw new ResponseStatusException(
	                HttpStatus.BAD_REQUEST, "Invalid product ID");
	    }

	    if (quantity == null || quantity <= 0) {
	        throw new ResponseStatusException(
	                HttpStatus.BAD_REQUEST,
	                "Quantity must be greater than zero");
	    }

	    if (operationKey == null || operationKey.isBlank()
	            || operationKey.trim().length() > 150) {
	        throw new ResponseStatusException(
	                HttpStatus.BAD_REQUEST, "Valid operation key is required");
	    }
	}

	private void validateSameOperation(
	        StockOperation op,
	        Long productId,
	        Integer quantity,
	        OperationType expectedType) {

	    if (!op.getProductId().equals(productId)
	            || !op.getQuantity().equals(quantity)
	            || op.getOperationType() != expectedType) {
	        throw new ResponseStatusException(
	                HttpStatus.CONFLICT,
	                "Operation key was reused with different stock details");
	    }
	}
}

