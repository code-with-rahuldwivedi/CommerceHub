package com.shopsphere.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles({"test", "h2"})
class OrderServiceApplicationTests {

	@DynamicPropertySource
	static void jwks(DynamicPropertyRegistry registry) {
		registry.add("security.jwt.jwks-uri", JwksTestServer::jwksUri);
	}

	@Test
	void contextLoads() {
	}
}