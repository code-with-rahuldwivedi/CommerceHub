package com.shopsphere.userservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopsphere.userservice.entity.User;

class AdminApiIntegrationTest extends ApiTestSupport {

    private org.springframework.test.web.servlet.ResultActions adminPut(String token, String path, String json)
            throws Exception {
        return mvc.perform(put(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long registerUser(String email) throws Exception {
        return body(register(email, PASSWORD).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void list_isPaged_filterable_andNeverExposesPasswords() throws Exception {
        String admin = adminAccessToken();
        String email = uniqueEmail();
        registerUser(email);

        mvc.perform(get("/api/admin/users").param("email", email.substring(0, 20)).param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].email").value(email))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.content[0].locked").value(false));

        mvc.perform(get("/api/admin/users").param("size", "1000").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUser_returnsDetails_or404() throws Exception {
        String admin = adminAccessToken();
        long id = registerUser(uniqueEmail());

        mvc.perform(get("/api/admin/users/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(get("/api/admin/users/999999999").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void disablingAUser_blocksLoginAndRefresh_immediately_andEnablingRestoresIt() throws Exception {
        String admin = adminAccessToken();
        String email = uniqueEmail();
        long id = registerUser(email);
        String refreshToken = body(login(email, PASSWORD).andExpect(status().isOk())).get("refreshToken").asText();

        adminPut(admin, "/api/admin/users/" + id + "/enabled", "{\"enabled\":false}").andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        refresh(refreshToken).andExpect(status().isUnauthorized());

        adminPut(admin, "/api/admin/users/" + id + "/enabled", "{\"enabled\":true}").andExpect(status().isNoContent());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void admin_cannotDisableOrDemoteThemselves() throws Exception {
        String adminEmail = uniqueEmail();
        User admin = createAdmin(adminEmail);
        String token = body(login(adminEmail, PASSWORD).andExpect(status().isOk())).get("accessToken").asText();

        adminPut(token, "/api/admin/users/" + admin.getId() + "/enabled", "{\"enabled\":false}")
                .andExpect(status().isConflict());
        adminPut(token, "/api/admin/users/" + admin.getId() + "/role", "{\"role\":\"USER\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void roleChange_takesEffectOnTheNextRefresh() throws Exception {
        String admin = adminAccessToken();
        String email = uniqueEmail();
        long id = registerUser(email);
        JsonNode tokens = body(login(email, PASSWORD).andExpect(status().isOk()));

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(tokens.get("accessToken").asText())))
                .andExpect(status().isForbidden());

        adminPut(admin, "/api/admin/users/" + id + "/role", "{\"role\":\"ADMIN\"}").andExpect(status().isNoContent());

        String refreshed = body(refresh(tokens.get("refreshToken").asText()).andExpect(status().isOk()))
                .get("accessToken").asText();
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(refreshed)))
                .andExpect(status().isOk());
    }

    @Test
    void invalidRole_returns400() throws Exception {
        String admin = adminAccessToken();
        long id = registerUser(uniqueEmail());

        adminPut(admin, "/api/admin/users/" + id + "/role", "{\"role\":\"SUPERUSER\"}").andExpect(status().isBadRequest());
        adminPut(admin, "/api/admin/users/" + id + "/role", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void unlock_letsALockedUserLogInAgain() throws Exception {
        String admin = adminAccessToken();
        String email = uniqueEmail();
        long id = registerUser(email);
        for (int i = 0; i < 3; i++) {
            login(email, "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/users/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.locked").value(true));

        mvc.perform(post("/api/admin/users/" + id + "/unlock").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unlock_unknownUser_returns404() throws Exception {
        String admin = adminAccessToken();

        mvc.perform(post("/api/admin/users/999999999/unlock").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }
}
