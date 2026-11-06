package com.shopsphere.userservice;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "h2"})
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void main_startsSpringBootApplication() {

        String[] args = {"--spring.profiles.active=test"};

        try (var springApplication = mockStatic(SpringApplication.class)) {

            UserServiceApplication.main(args);

            springApplication.verify(() ->
                    SpringApplication.run(UserServiceApplication.class, args)
            );
        }
    }
}
