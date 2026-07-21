package com.shopsphere.cloud.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {

    @GetMapping("/userServiceFallBack")
    public ResponseEntity<String> userServiceFallBack() {
        return new ResponseEntity<>(
                "User Service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @GetMapping("/productServiceFallBack")
    public ResponseEntity<String> productServiceFallBack() {
        return new ResponseEntity<>(
                "Product Service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}