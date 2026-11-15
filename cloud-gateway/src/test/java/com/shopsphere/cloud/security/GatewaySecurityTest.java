package com.shopsphere.cloud.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The edge rules: deny by default, public only what must be public, inventory mutation never reachable. */
@SpringBootTest
@AutoConfigureMockMvc
class GatewaySecurityTest {

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("7").claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("1").claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void ordersRequireAToken() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void customerCannotReachTheBackOffice() throws Exception {
        mvc.perform(get("/api/admin/orders").with(user())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(user())).andExpect(status().isForbidden());
    }

    @Test
    void customerCannotWriteTheCatalog() throws Exception {
        mvc.perform(post("/api/products").with(user())).andExpect(status().isForbidden());
    }

    @Test
    void stockMutationIsNeverReachableFromOutsideEvenForAdmins() throws Exception {
        mvc.perform(post("/api/inventory/5/reduce").with(admin())).andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/5/restore").with(admin())).andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/5/reduce")).andExpect(status().isUnauthorized());
    }

    @Test
    void inventoryReadsNeedAnAdmin() throws Exception {
        mvc.perform(get("/api/inventory/5")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inventory/5").with(user())).andExpect(status().isForbidden());
    }

    @Test
    void unknownPathsAreDeniedByDefault() throws Exception {
        mvc.perform(get("/internal/anything").with(admin())).andExpect(status().isForbidden());
    }

    @Test
    void fallbackEndpointIsNotReachableDirectly() throws Exception {
        mvc.perform(get("/fallback/order-service")).andExpect(status().isUnauthorized());
        mvc.perform(get("/fallback/order-service").with(admin())).andExpect(status().isForbidden());
    }

    @Test
    void publicCatalogReadsAndAuthFlowsPassAuthorization() throws Exception {
        assertReachesProxy(mvc, get("/api/products"));
        assertReachesProxy(mvc, get("/api/products/5"));
        assertReachesProxy(mvc, post("/api/auth/login"));
    }

    @Test
    void corsPreflightFromAllowedOriginSucceeds() throws Exception {
        mvc.perform(options("/api/orders")
                        .header("Origin", "https://shop.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://shop.example.com"));
    }

    @Test
    void corsPreflightFromOtherOriginIsRefused() throws Exception {
        mvc.perform(options("/api/orders")
                        .header("Origin", "https://evil.example.org")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    /** No downstream service exists in this test, so a request that passed authorization ends in a proxy error. */
    private static void assertReachesProxy(MockMvc mvc,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        int status;
        try {
            status = mvc.perform(request).andReturn().getResponse().getStatus();
        } catch (Exception proxyFailure) {
            status = 500;
        }
        assertThat(status).isNotIn(401, 403);
    }
}
