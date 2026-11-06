package com.shopsphere.userservice;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest(properties = "app.rate-limit.auth-requests-per-minute=3")
class RateLimitIntegrationTest extends ApiTestSupport {

    @Test
    void afterTheLimitIsReached_requestsGet429_withRetryAfter() throws Exception {

        String email = uniqueEmail();

        for (int i = 0; i < 3; i++) {
            login(email, PASSWORD).andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string(
                        "Content-Type",
                        org.hamcrest.Matchers.containsString(
                                MediaType.APPLICATION_PROBLEM_JSON_VALUE)));
    }

    @Test
    void limits_areTrackedPerEndpoint() throws Exception {

        String testIp = "192.0.2.20";

        for (int i = 0; i < 4; i++) {
            postJsonFromIp(
                    testIp,
                    "/api/auth/refresh",
                    Map.of("refreshToken", "x" + i));
        }

        // Refresh limit must not affect login limit for this IP.
        postJsonFromIp(
                testIp,
                "/api/auth/login",
                Map.of("email", uniqueEmail(), "password", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions postJsonFromIp(
            String ip, String path, Object body) throws Exception {

        return mvc.perform(post(path)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }
}