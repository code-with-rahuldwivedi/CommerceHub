package com.shopsphere.productservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** MockMvc tests with the REAL security chain: tokens are verified against an embedded JWKS server. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "h2"})
abstract class ApiTestSupport {

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.jwks-uri", JwksTestServer::jwksUri);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String admin() {
        return bearer(JwksTestServer.adminToken());
    }

    protected static String customer() {
        return bearer(JwksTestServer.userToken());
    }

    protected static String uniqueTag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected static Map<String, Object> product(String name, String price, String category) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "Description of " + name);
        body.put("price", new java.math.BigDecimal(price));
        body.put("category", category);
        body.put("imageUrl", "https://cdn.example.com/" + name.replace(' ', '-') + ".png");
        return body;
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }

    protected ResultActions send(MockHttpServletRequestBuilder builder, String authorization) throws Exception {
        return mvc.perform(authorization == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, authorization));
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        return mapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    /** Creates a product as admin and returns the response JSON. */
    protected JsonNode createProduct(String name, String price, String category) throws Exception {
        return body(send(json(post("/api/products"), product(name, price, category)), admin())
                .andExpect(status().isCreated()));
    }
}
