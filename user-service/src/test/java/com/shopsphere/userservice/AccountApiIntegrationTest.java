package com.shopsphere.userservice;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

class AccountApiIntegrationTest extends ApiTestSupport {

    private static final String NEW_PASSWORD = "An0ther-Str0ng-Pass!";

    // ---------- forgot / reset password ----------

    @Test
    void forgotPassword_unknownEmail_returns202_andSendsNothing() throws Exception {
        postJson("/api/auth/forgot-password", Map.of("email", uniqueEmail())).andExpect(status().isAccepted());

        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void fullPasswordResetFlow() throws Exception {
        String email = uniqueEmail();
        String oldRefresh = registerAndLogin(email).get("refreshToken").asText();

        postJson("/api/auth/forgot-password", Map.of("email", email.toUpperCase())).andExpect(status().isAccepted());
        String resetToken = capturedResetToken(email);

        postJson("/api/auth/reset-password", Map.of("token", resetToken, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        refresh(oldRefresh).andExpect(status().isUnauthorized());          // all sessions were revoked
        login(email, PASSWORD).andExpect(status().isUnauthorized());       // old password is dead
        login(email, NEW_PASSWORD).andExpect(status().isOk());             // new one works
        verify(mailService).sendPasswordChangedEmail(email);

        // the link is single use
        postJson("/api/auth/reset-password", Map.of("token", resetToken, "newPassword", "Yet-An0ther-Pass!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_withWeakPassword_failsWithoutBurningTheToken() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        postJson("/api/auth/forgot-password", Map.of("email", email)).andExpect(status().isAccepted());
        String resetToken = capturedResetToken(email);

        postJson("/api/auth/reset-password", Map.of("token", resetToken, "newPassword", "password123"))
                .andExpect(status().isBadRequest());

        postJson("/api/auth/reset-password", Map.of("token", resetToken, "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());
    }

    @Test
    void resetPassword_clearsAnActiveLockout() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        for (int i = 0; i < 3; i++) {
            login(email, "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isUnauthorized()); // locked

        postJson("/api/auth/forgot-password", Map.of("email", email)).andExpect(status().isAccepted());
        postJson("/api/auth/reset-password", Map.of("token", capturedResetToken(email), "newPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resetPassword_withGarbageToken_returns400() throws Exception {
        postJson("/api/auth/reset-password", Map.of("token", "garbage-token", "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword_twiceInARow_mailsOnlyOnce_dueToCooldown() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        postJson("/api/auth/forgot-password", Map.of("email", email)).andExpect(status().isAccepted());
        postJson("/api/auth/forgot-password", Map.of("email", email)).andExpect(status().isAccepted());

        capturedResetToken(email); // verify(...) defaults to times(1): fails if a second mail was sent
    }

    // ---------- change password ----------

    @Test
    void changePassword_wrongCurrentPassword_returns400() throws Exception {
        String access = registerAndLogin(uniqueEmail()).get("accessToken").asText();

        mvc.perform(post("/api/user/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("currentPassword", "wrong-one-1", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_success_revokesSessions_andSwitchesCredentials() throws Exception {
        String email = uniqueEmail();
        JsonNode tokens = registerAndLogin(email);

        mvc.perform(post("/api/user/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.get("accessToken").asText()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        refresh(tokens.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        verify(mailService).sendPasswordChangedEmail(email);
    }

    @Test
    void changePassword_toACommonPassword_returns400() throws Exception {
        String access = registerAndLogin(uniqueEmail()).get("accessToken").asText();

        mvc.perform(post("/api/user/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "password123"))))
                .andExpect(status().isBadRequest());
    }

    // ---------- sessions / profile ----------

    @Test
    void deleteSessions_signsOutEverywhere() throws Exception {
        String email = uniqueEmail();
        JsonNode first = registerAndLogin(email);
        String secondRefresh = body(login(email, PASSWORD).andExpect(status().isOk())).get("refreshToken").asText();

        mvc.perform(delete("/api/user/me/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.get("accessToken").asText())))
                .andExpect(status().isNoContent());

        refresh(first.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        refresh(secondRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_changesTheName() throws Exception {
        String access = registerAndLogin(uniqueEmail()).get("accessToken").asText();

        mvc.perform(patch("/api/user/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  New Name  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));

        mvc.perform(get("/api/user/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateProfile_blankName_returns400() throws Exception {
        String access = registerAndLogin(uniqueEmail()).get("accessToken").asText();

        mvc.perform(patch("/api/user/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- e-mail verification ----------

    @Test
    void verifyEmail_flow_marksTheAccountVerified_andTokenIsSingleUse() throws Exception {
        String email = uniqueEmail();
        String access = registerAndLogin(email).get("accessToken").asText();
        String token = capturedVerificationToken(email);

        mvc.perform(get("/api/user/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(jsonPath("$.emailVerified").value(false));

        postJson("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isNoContent());

        mvc.perform(get("/api/user/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(jsonPath("$.emailVerified").value(true));
        postJson("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isBadRequest());
        assertTrue(users.findByEmail(email).orElseThrow().isEmailVerified());
    }

    @Test
    void resendVerification_unknownEmail_returns202_andSendsNothing() throws Exception {
        postJson("/api/auth/resend-verification", Map.of("email", uniqueEmail())).andExpect(status().isAccepted());

        verify(mailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void verifyEmail_withGarbageToken_returns400() throws Exception {
        postJson("/api/auth/verify-email", Map.of("token", "garbage")).andExpect(status().isBadRequest());
    }
}
