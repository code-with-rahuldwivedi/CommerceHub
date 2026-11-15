package com.shopsphere.cloud.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FallbackControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FallbackController()).build();

    @Test
    void postIsAnsweredWith503AndRetryGuidance() throws Exception {
        mvc.perform(post("/fallback/order-service"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.detail", containsString("Idempotency-Key")))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void getIsAnsweredWith503() throws Exception {
        mvc.perform(get("/fallback/product-service"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", containsString("temporarily unavailable")));
    }

    @Test
    void unknownServiceNameStillGetsAGenericAnswer() throws Exception {
        mvc.perform(get("/fallback/something-else"))
                .andExpect(status().isServiceUnavailable());
    }
}
