package com.shopsphere.userservice.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopsphere.userservice.security.JwtKeyStore;

/** Public keys other services (and the gateway) use to verify access tokens. */
@RestController
public class JwksController {

    private final JwtKeyStore keyStore;

    public JwksController(JwtKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(keyStore.jwks());
    }
}
