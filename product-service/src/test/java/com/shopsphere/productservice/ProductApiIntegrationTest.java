package com.shopsphere.productservice;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

class ProductApiIntegrationTest extends ApiTestSupport {

    // ================================================================ authentication / authorization

    @Test
    void anonymousUsers_canReadTheCatalog() throws Exception {
        String tag = uniqueTag();
        JsonNode created = createProduct("Public " + tag, "10.00", "BOOKS");

        send(get("/api/products").param("q", tag), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        send(get("/api/products/" + created.get("id").asLong()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Public " + tag));
    }

    @Test
    void writes_requireAnAdminToken() throws Exception {
        Map<String, Object> body = product("Nope " + uniqueTag(), "10.00", "BOOKS");

        send(json(post("/api/products"), body), null).andExpect(status().isUnauthorized());
        send(json(post("/api/products"), body), customer()).andExpect(status().isForbidden());
        send(json(post("/api/products"), body), admin()).andExpect(status().isCreated());
    }

    @Test
    void unauthenticatedResponses_areProblemJson_withABearerChallenge() throws Exception {
        send(json(post("/api/products"), product("X", "1.00", "BOOKS")), null)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(401));

        send(json(post("/api/products"), product("X", "1.00", "BOOKS")), customer())
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void invalidTokens_areRejected_evenOnPublicEndpoints() throws Exception {
        send(get("/api/products"), bearer("garbage")).andExpect(status().isUnauthorized());
        send(get("/api/products"), bearer(JwksTestServer.expiredToken())).andExpect(status().isUnauthorized());
        send(get("/api/products"), bearer(JwksTestServer.tokenWithAudience("other"))).andExpect(status().isUnauthorized());
        send(get("/api/products"), bearer(JwksTestServer.tokenWithIssuer("other"))).andExpect(status().isUnauthorized());
        send(get("/api/products"), bearer(JwksTestServer.tokenSignedByImpostor())).andExpect(status().isUnauthorized());
        send(get("/api/products"), bearer(JwksTestServer.hmacToken())).andExpect(status().isUnauthorized());
        send(get("/api/products"), bearer(JwksTestServer.unsignedToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRoutes_requireAuthentication() throws Exception {
        send(get("/api/does-not-exist"), null).andExpect(status().isUnauthorized());
    }

    // ================================================================ create / validation

    @Test
    void create_returns201_withLocation_andDefaults() throws Exception {
        String name = "Phone " + uniqueTag();

        send(json(post("/api/products"), product(name, "199.99", "MOBILES")), admin())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/products/")))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.price").value(199.99))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").doesNotExist());
    }

    @Test
    void create_withInvalidBody_returns400_withFieldErrors() throws Exception {
        Map<String, Object> bad = product("", "-1", "BOOKS");
        bad.put("description", "");
        bad.put("imageUrl", "javascript:alert(1)");

        send(json(post("/api/products"), bad), admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.imageUrl").exists());
    }

    @Test
    void create_priceWithTooManyDecimalsOrDigits_returns400() throws Exception {
        send(json(post("/api/products"), product("P", "10.999", "BOOKS")), admin())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.price").exists());
        send(json(post("/api/products"), product("P", "123456789.00", "BOOKS")), admin())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.price").exists());
        send(json(post("/api/products"), product("P", "0", "BOOKS")), admin())
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_imageUrlMustBeHttpOrHttps() throws Exception {
        for (String url : new String[] {"ftp://example.com/a.png", "file:///etc/passwd", "data:text/html,x", "not a url"}) {
            Map<String, Object> body = product("P", "10.00", "BOOKS");
            body.put("imageUrl", url);
            send(json(post("/api/products"), body), admin())
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.imageUrl").exists());
        }
    }

    @Test
    void create_withoutAnImageUrl_isAllowed() throws Exception {
        Map<String, Object> body = product("P " + uniqueTag(), "10.00", "BOOKS");
        body.remove("imageUrl");

        send(json(post("/api/products"), body), admin())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void create_unknownCategory_orMalformedJson_returns400() throws Exception {
        Map<String, Object> body = product("P", "10.00", "WEAPONS");
        send(json(post("/api/products"), body), admin()).andExpect(status().isBadRequest());

        send(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{not json"), admin())
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_ignoresClientSuppliedIdStatusAndTimestamps() throws Exception {
        Map<String, Object> body = product("Mass " + uniqueTag(), "10.00", "BOOKS");
        body.put("id", 999999);
        body.put("status", "ARCHIVED");
        body.put("createdAt", "2000-01-01T00:00:00");

        JsonNode created = body(send(json(post("/api/products"), body), admin()).andExpect(status().isCreated()));

        assertEquals("ACTIVE", created.get("status").asText());
        assertEquals(false, created.get("id").asLong() == 999999);
        assertEquals(false, created.get("createdAt").asText().startsWith("2000"));
    }

    @Test
    void oversizedBody_isRejectedWith413() throws Exception {
        Map<String, Object> body = product("P", "10.00", "BOOKS");
        body.put("description", "x".repeat(20_000));

        send(json(post("/api/products"), body), admin()).andExpect(status().isPayloadTooLarge());
    }

    // ================================================================ read / paging / filters

    @Test
    void get_unknownOrInvalidId() throws Exception {
        send(get("/api/products/999999999"), null).andExpect(status().isNotFound());
        send(get("/api/products/0"), null).andExpect(status().isBadRequest());
        send(get("/api/products/-5"), null).andExpect(status().isBadRequest());
        send(get("/api/products/abc"), null).andExpect(status().isBadRequest());
    }

    @Test
    void search_filtersSortsAndPages() throws Exception {
        String tag = uniqueTag();
        createProduct("Cheap " + tag, "10.00", "BOOKS");
        createProduct("Mid " + tag, "50.00", "BOOKS");
        createProduct("Pricey " + tag, "90.00", "TOYS");

        send(get("/api/products").param("q", tag).param("sort", "price,asc"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].price").value(10.0))
                .andExpect(jsonPath("$.content[2].price").value(90.0))
                .andExpect(jsonPath("$.page.totalElements").value(3));

        send(get("/api/products").param("q", tag).param("sort", "price,desc").param("size", "1").param("page", "1"), null)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].price").value(50.0))
                .andExpect(jsonPath("$.page.totalPages").value(3));

        send(get("/api/products").param("q", tag).param("category", "TOYS"), null)
                .andExpect(jsonPath("$.content", hasSize(1)));
        send(get("/api/products").param("q", tag).param("minPrice", "20").param("maxPrice", "60"), null)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Mid " + tag));
    }

    @Test
    void search_wildcardsInTheQueryAreLiteral() throws Exception {
        String tag = uniqueTag();
        createProduct("disc%unt-" + tag, "10.00", "BOOKS");
        createProduct("discxunt-" + tag, "10.00", "BOOKS");

        send(get("/api/products").param("q", "disc%unt-" + tag), null)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("disc%unt-" + tag));
    }

    @Test
    void search_rejectsBadParameters() throws Exception {
        send(get("/api/products").param("size", "1000"), null).andExpect(status().isBadRequest());
        send(get("/api/products").param("size", "0"), null).andExpect(status().isBadRequest());
        send(get("/api/products").param("page", "-1"), null).andExpect(status().isBadRequest());
        send(get("/api/products").param("sort", "password,asc"), null).andExpect(status().isBadRequest());
        send(get("/api/products").param("sort", "price,sideways"), null).andExpect(status().isBadRequest());
        send(get("/api/products").param("category", "WEAPONS"), null).andExpect(status().isBadRequest());
        send(get("/api/products").param("minPrice", "50").param("maxPrice", "10"), null)
                .andExpect(status().isBadRequest());
        send(get("/api/products").param("minPrice", "-1"), null).andExpect(status().isBadRequest());
    }

    // ================================================================ update / optimistic locking

    @Test
    void update_withTheCurrentVersion_succeeds_andIncrementsIt() throws Exception {
        JsonNode created = createProduct("Upd " + uniqueTag(), "10.00", "BOOKS");
        long id = created.get("id").asLong();
        Map<String, Object> changed = product("Renamed " + uniqueTag(), "12.50", "TOYS");
        changed.put("version", created.get("version").asLong());

        send(json(put("/api/products/" + id), changed), admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(changed.get("name")))
                .andExpect(jsonPath("$.price").value(12.5))
                .andExpect(jsonPath("$.category").value("TOYS"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void update_withAStaleVersion_returns409() throws Exception {
        JsonNode created = createProduct("Stale " + uniqueTag(), "10.00", "BOOKS");
        long id = created.get("id").asLong();

        Map<String, Object> first = product("First " + uniqueTag(), "11.00", "BOOKS");
        first.put("version", 0);
        send(json(put("/api/products/" + id), first), admin()).andExpect(status().isOk());

        Map<String, Object> second = product("Second " + uniqueTag(), "12.00", "BOOKS");
        second.put("version", 0); // still the old version
        send(json(put("/api/products/" + id), second), admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("modified")));
    }

    @Test
    void update_withoutAVersion_isAccepted_forLegacyClients() throws Exception {
        long id = createProduct("NoVer " + uniqueTag(), "10.00", "BOOKS").get("id").asLong();

        send(json(put("/api/products/" + id), product("After " + uniqueTag(), "11.00", "BOOKS")), admin())
                .andExpect(status().isOk());
    }

    @Test
    void update_unknownProduct_returns404() throws Exception {
        send(json(put("/api/products/999999999"), product("X", "1.00", "BOOKS")), admin())
                .andExpect(status().isNotFound());
    }

    // ================================================================ visibility / soft delete

    @Test
    void delete_isASoftDelete_hiddenFromCustomers_visibleToAdmins_andIdempotent() throws Exception {
        String tag = uniqueTag();
        long id = createProduct("Doomed " + tag, "10.00", "BOOKS").get("id").asLong();

        send(delete("/api/products/" + id), customer()).andExpect(status().isForbidden());
        send(delete("/api/products/" + id), admin()).andExpect(status().isNoContent());
        send(delete("/api/products/" + id), admin()).andExpect(status().isNoContent());

        send(get("/api/products/" + id), null).andExpect(status().isNotFound());
        send(get("/api/products/" + id), customer()).andExpect(status().isNotFound());
        send(get("/api/products").param("q", tag), null).andExpect(jsonPath("$.content", hasSize(0)));

        send(get("/api/products/" + id), admin())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        send(get("/api/products").param("q", tag), admin()).andExpect(jsonPath("$.content", hasSize(0)));
        send(get("/api/products").param("q", tag).param("status", "ARCHIVED"), admin())
                .andExpect(jsonPath("$.content", hasSize(1)));

        send(json(put("/api/products/" + id), product("Edit " + tag, "11.00", "BOOKS")), admin())
                .andExpect(status().isConflict());
    }

    @Test
    void status_unpublishHidesFromCustomers_andArchivedCanBeRestored() throws Exception {
        String tag = uniqueTag();
        long id = createProduct("Toggle " + tag, "10.00", "BOOKS").get("id").asLong();

        send(json(put("/api/products/" + id + "/status"), Map.of("status", "INACTIVE")), admin())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

        send(get("/api/products").param("q", tag), null).andExpect(jsonPath("$.content", hasSize(0)));
        send(get("/api/products").param("q", tag), customer()).andExpect(jsonPath("$.content", hasSize(0)));
        send(get("/api/products").param("q", tag), admin()).andExpect(jsonPath("$.content", hasSize(1)));
        send(get("/api/products").param("q", tag).param("status", "INACTIVE"), customer())
                .andExpect(jsonPath("$.content", hasSize(0)));

        send(delete("/api/products/" + id), admin()).andExpect(status().isNoContent());
        send(json(put("/api/products/" + id + "/status"), Map.of("status", "ACTIVE")), admin())
                .andExpect(status().isOk());
        send(get("/api/products/" + id), null).andExpect(status().isOk());
    }

    @Test
    void status_requiresAdmin_andAValidValue() throws Exception {
        long id = createProduct("St " + uniqueTag(), "10.00", "BOOKS").get("id").asLong();

        send(json(put("/api/products/" + id + "/status"), Map.of("status", "INACTIVE")), customer())
                .andExpect(status().isForbidden());
        send(json(put("/api/products/" + id + "/status"), Map.of("status", "DELETED")), admin())
                .andExpect(status().isBadRequest());
        send(json(put("/api/products/" + id + "/status"), Map.of()), admin())
                .andExpect(status().isBadRequest());
    }

    // ================================================================ batch

    @Test
    void batch_returnsOnlyActiveProducts_andSkipsUnknownIds() throws Exception {
        String tag = uniqueTag();
        long active = createProduct("BatchA " + tag, "10.00", "BOOKS").get("id").asLong();
        long hidden = createProduct("BatchB " + tag, "10.00", "BOOKS").get("id").asLong();
        send(json(put("/api/products/" + hidden + "/status"), Map.of("status", "INACTIVE")), admin())
                .andExpect(status().isOk());

        send(get("/api/products/batch").param("ids", active + "," + hidden + ",999999999," + active), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(active));
    }

    @Test
    void batch_validatesItsInput() throws Exception {
        send(get("/api/products/batch"), null).andExpect(status().isBadRequest());
        send(get("/api/products/batch").param("ids", "0"), null).andExpect(status().isBadRequest());
        send(get("/api/products/batch").param("ids", "abc"), null).andExpect(status().isBadRequest());

        String tooMany = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(","));
        send(get("/api/products/batch").param("ids", tooMany), null).andExpect(status().isBadRequest());
    }

    // ================================================================ legacy routes

    @Test
    void legacyRoutes_stillWork_andAreMarkedDeprecated() throws Exception {
        String tag = uniqueTag();

        JsonNode created = body(send(json(post("/api/createProduct"), product("Legacy " + tag, "10.00", "BOOKS")), admin())
                .andExpect(status().isCreated())
                .andExpect(header().string("Deprecation", "true")));
        long id = created.get("id").asLong();

        send(get("/api/product/" + id), null)
                .andExpect(status().isOk()).andExpect(header().string("Deprecation", "true"));
        send(get("/api/getAllProducts"), null)
                .andExpect(status().isOk()).andExpect(header().string("Deprecation", "true"))
                .andExpect(jsonPath("$").isArray());
        send(json(put("/api/product/" + id), product("Legacy2 " + tag, "11.00", "BOOKS")), admin())
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Legacy2 " + tag));
        send(delete("/api/product/" + id), admin()).andExpect(status().isNoContent());
        send(get("/api/product/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void legacyRoutes_keepTheirAuthorizationRules() throws Exception {
        Map<String, Object> body = product("L " + uniqueTag(), "10.00", "BOOKS");

        send(json(post("/api/createProduct"), body), null).andExpect(status().isUnauthorized());
        send(json(post("/api/createProduct"), body), customer()).andExpect(status().isForbidden());
        send(delete("/api/product/1"), customer()).andExpect(status().isForbidden());
        send(json(put("/api/product/1"), body), customer()).andExpect(status().isForbidden());
    }

    // ================================================================ misc

    @Test
    void responses_carryARequestId() throws Exception {
        send(get("/api/products"), null).andExpect(header().exists("X-Request-Id"));

        mvc.perform(get("/api/products").header("X-Request-Id", "client-req-12345"))
                .andExpect(header().string("X-Request-Id", "client-req-12345"));
        mvc.perform(get("/api/products").header("X-Request-Id", "bad id & junk"))
                .andExpect(header().string("X-Request-Id", not("bad id & junk")));
    }
}
