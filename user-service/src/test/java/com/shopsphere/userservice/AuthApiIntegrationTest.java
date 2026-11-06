package com.shopsphere.userservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopsphere.userservice.entity.User;

class AuthApiIntegrationTest extends ApiTestSupport {

    // ---------- registration ----------

    @Test
    void register_returns201_andNeverExposesPasswordOrAcceptsRole() throws Exception {
        String email = uniqueEmail();

        postJson("/api/auth/register",
                Map.of("name", "Test", "email", email, "password", PASSWORD, "role", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_duplicateEmail_returns409_caseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD).andExpect(status().isConflict());
    }

    @Test
    void register_invalidBody_returns400_withFieldErrors() throws Exception {
        postJson("/api/auth/register", Map.of("name", "", "email", "not-an-email", "password", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void register_commonPassword_returns400() throws Exception {
        register(uniqueEmail(), "password123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("too common")));
    }

    @Test
    void register_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_sendsAVerificationEmail() throws Exception {
        String email = uniqueEmail();

        register(email, PASSWORD).andExpect(status().isCreated());

        assertNotNull(capturedVerificationToken(email));
    }

    @Test
    void oversizedBody_isRejectedWith413_beforeAnyProcessing() throws Exception {
        String hugeName = "x".repeat(20_000);

        postJson("/api/auth/register", Map.of("name", hugeName, "email", uniqueEmail(), "password", PASSWORD))
                .andExpect(status().isPayloadTooLarge());
    }

    // ---------- login ----------

    @Test
    void login_unknownUser_returns401() throws Exception {
        login(uniqueEmail(), PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void login_wrongPassword_hasSameBodyAsUnknownUser() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        JsonNode wrongPassword = body(login(email, "wrong-password-1").andExpect(status().isUnauthorized()));
        JsonNode unknownUser = body(login(uniqueEmail(), "wrong-password-1").andExpect(status().isUnauthorized()));

        assertEquals(unknownUser.get("detail"), wrongPassword.get("detail"));
    }

    @Test
    void login_isCaseInsensitiveOnEmail() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        login(email.toUpperCase(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void responses_carryARequestId_andEchoAValidOneFromTheClient() throws Exception {
        login(uniqueEmail(), PASSWORD).andExpect(header().exists("X-Request-Id"));

        mvc.perform(post("/api/auth/login")
                        .header("X-Request-Id", "client-req-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever123\"}"))
                .andExpect(header().string("X-Request-Id", "client-req-12345"));

        mvc.perform(post("/api/auth/login")
                        .header("X-Request-Id", "bad id with spaces & junk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever123\"}"))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("bad id with spaces & junk")));
    }

    // ---------- token lifecycle ----------

    @Test
    void fullFlow_login_me_refreshRotation_replayRevokesSession() throws Exception {
        String email = uniqueEmail();
        JsonNode tokens = registerAndLogin(email);
        String access = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();
        assertEquals("Bearer", tokens.get("tokenType").asText());

        mvc.perform(get("/api/user/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        JsonNode rotated = body(refresh(refreshToken).andExpect(status().isOk()));
        String newRefresh = rotated.get("refreshToken").asText();
        assertNotEquals(refreshToken, newRefresh);

        // replaying the already-used token fails AND kills the whole session
        refresh(refreshToken).andExpect(status().isUnauthorized());
        refresh(newRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesRefreshToken_andIsIdempotent() throws Exception {
        String refreshToken = registerAndLogin(uniqueEmail()).get("refreshToken").asText();

        postJson("/api/auth/logout", Map.of("refreshToken", refreshToken)).andExpect(status().isNoContent());
        postJson("/api/auth/logout", Map.of("refreshToken", refreshToken)).andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withGarbageToken_returns401() throws Exception {
        refresh("this-token-does-not-exist").andExpect(status().isUnauthorized());
    }

    // ---------- authorization ----------

    @Test
    void protectedEndpoint_withoutToken_returns401_withBearerChallenge() throws Exception {
        mvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {
        mvc.perform(get("/api/user/me").header(HttpHeaders.AUTHORIZATION, bearer("abc.def.ghi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userToken_cannotAccessAdminEndpoints() throws Exception {
        String access = registerAndLogin(uniqueEmail()).get("accessToken").asText();

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRoute_requiresAuthentication_insteadOfRevealingWhatExists() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
    }

    // ---------- brute-force protection ----------

    @Test
    void repeatedFailures_lockTheAccount_evenForTheCorrectPassword() throws Exception {
        String email = uniqueEmail();

        register(email, PASSWORD)
                .andExpect(status().isCreated());

        for (int i = 0; i < 3; i++) {
            login(email, "wrong-password-" + i)
                    .andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD)
                .andExpect(status().isUnauthorized());

        assertNotNull(
                users.findByEmail(email).orElseThrow().getLockedUntil()
        );
    }

    @Test
    void successfulLogin_resetsFailureCounter() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        login(email, "wrong-password-1").andExpect(status().isUnauthorized());
        login(email, "wrong-password-2").andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isOk());

        assertEquals(0, users.findByEmail(email).orElseThrow().getFailedAttempts());
    }
}
