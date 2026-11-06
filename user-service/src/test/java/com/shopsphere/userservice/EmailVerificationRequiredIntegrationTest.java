package com.shopsphere.userservice;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.account.require-email-verification=true")
class EmailVerificationRequiredIntegrationTest extends ApiTestSupport {

    @Test
    void login_isBlockedUntilTheEmailIsVerified() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        // correct password, unverified: explicit 403
        login(email, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Email address is not verified"));

        // wrong password must NOT reveal anything about verification state
        login(email, "wrong-password-1").andExpect(status().isUnauthorized());

        postJson("/api/auth/verify-email", Map.of("token", capturedVerificationToken(email)))
                .andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void passwordReset_alsoVerifiesTheAddress() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        postJson("/api/auth/forgot-password", Map.of("email", email)).andExpect(status().isAccepted());
        postJson("/api/auth/reset-password",
                Map.of("token", capturedResetToken(email), "newPassword", "An0ther-Str0ng-Pass!"))
                .andExpect(status().isNoContent());

        login(email, "An0ther-Str0ng-Pass!").andExpect(status().isOk());
    }
}
