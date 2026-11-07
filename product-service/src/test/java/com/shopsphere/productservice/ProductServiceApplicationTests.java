package com.shopsphere.productservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles({"test", "h2"})
class ProductServiceApplicationTests {

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.jwks-uri", JwksTestServer::jwksUri);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void mainMethodShouldInvokeSpringApplicationRun() {

        String[] args = {"--spring.main.banner-mode=off"};

        try (MockedStatic<SpringApplication> mocked =
                     Mockito.mockStatic(SpringApplication.class)) {

            ProductServiceApplication.main(args);

            mocked.verify(
                    () -> SpringApplication.run(
                            ProductServiceApplication.class, args
                    ),
                    times(1)
            );
        }
    }
}
