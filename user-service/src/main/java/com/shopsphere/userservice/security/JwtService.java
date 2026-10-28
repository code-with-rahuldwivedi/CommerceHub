package com.shopsphere.userservice.security;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.shopsphere.userservice.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    //Convert Base64 Secret into Key
    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    //Generate JWT Token
    public String generateToken(User user) {

    	 Map<String, Object> claims = new HashMap<>();
    	    claims.put("role", user.getRole().name());
	    claims.put("userId", user.getId());

    	    return Jwts.builder()
    	            .claims(claims)
    	            .subject(user.getEmail())
    	            .issuedAt(new Date())
    	            .expiration(new Date(System.currentTimeMillis() + expiration))
    	            .signWith(getSignKey())
    	            .compact();
    }

    //Extract Email from JWT Token
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    //Validate Token
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    //Check Token Expired or Not
    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    //Extract All Claims from JWT
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}