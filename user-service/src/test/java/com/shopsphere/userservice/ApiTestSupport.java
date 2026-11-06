package com.shopsphere.userservice;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.userservice.entity.Role;
import com.shopsphere.userservice.entity.User;
import com.shopsphere.userservice.repository.UserRepository;
import com.shopsphere.userservice.service.MailService;

/**
 * Shared helpers for MockMvc integration tests.
 *
 * Uses H2 database, real Spring Security filter chain
 * and mocked MailService.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
abstract class ApiTestSupport {

    protected static final String PASSWORD = "Str0ng-Passw0rd!";

    /*
     * Generates a different test IP for every test method.
     * This prevents shared rate-limit counters from interfering
     * with different integration tests.
     */
    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);

    protected String testIp;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected PasswordEncoder encoder;

    @MockitoBean
    protected MailService mailService;

    @BeforeEach
    void assignUniqueTestIp() {

        int id = IP_COUNTER.getAndIncrement();

        int second = (id >>> 16) & 255;
        int third = (id >>> 8) & 255;
        int fourth = id & 255;

        testIp = "10." + second + "." + third + "." + fourth;
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * Performs a POST JSON request using the current test IP.
     */
    protected ResultActions postJson(String path, Object body) throws Exception {

        return mvc.perform(post(path)
                .with(request -> {
                    request.setRemoteAddr(testIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    protected ResultActions register(String email, String password) throws Exception {

        return postJson(
                "/api/auth/register",
                Map.of(
                        "name", "Test User",
                        "email", email,
                        "password", password
                )
        );
    }

    protected ResultActions login(String email, String password) throws Exception {

        return postJson(
                "/api/auth/login",
                Map.of(
                        "email", email,
                        "password", password
                )
        );
    }

    protected ResultActions refresh(String refreshToken) throws Exception {

        return postJson(
                "/api/auth/refresh",
                Map.of("refreshToken", refreshToken)
        );
    }

    protected JsonNode body(ResultActions actions) throws Exception {

        return mapper.readTree(
                actions.andReturn()
                        .getResponse()
                        .getContentAsString()
        );
    }

    /**
     * Registers a fresh user and logs in.
     * Returns the token response JSON.
     */
    protected JsonNode registerAndLogin(String email) throws Exception {

        register(email, PASSWORD)
                .andExpect(status().isCreated());

        return body(
                login(email, PASSWORD)
                        .andExpect(status().isOk())
        );
    }

    protected User createAdmin(String email) {

        User admin = new User();

        admin.setName("Admin");
        admin.setEmail(email);
        admin.setPassword(encoder.encode(PASSWORD));
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);

        return users.save(admin);
    }

    protected String adminAccessToken() throws Exception {

        String email = uniqueEmail();

        createAdmin(email);

        return body(
                login(email, PASSWORD)
                        .andExpect(status().isOk())
        ).get("accessToken").asText();
    }

    /**
     * Captures the raw password reset token sent through MailService.
     */
    protected String capturedResetToken(String email) {

        ArgumentCaptor<String> token =
                ArgumentCaptor.forClass(String.class);

        verify(mailService)
                .sendPasswordResetEmail(eq(email), token.capture());

        return token.getValue();
    }

    /**
     * Captures the raw email verification token sent through MailService.
     */
    protected String capturedVerificationToken(String email) {

        ArgumentCaptor<String> token =
                ArgumentCaptor.forClass(String.class);

        verify(mailService)
                .sendVerificationEmail(
                        eq(email),
                        anyString(),
                        token.capture()
                );

        return token.getValue();
    }
}
