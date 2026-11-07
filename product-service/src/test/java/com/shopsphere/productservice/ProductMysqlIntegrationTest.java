package com.shopsphere.productservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs the real Flyway migrations on a real MySQL and boots with hibernate ddl-auto=validate, so any
 * mismatch between entities and migrations fails here instead of in production. Also exercises the
 * price CHECK constraint and optimistic locking under real concurrency.
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // no "h2" profile: the datasource comes from the container
@Testcontainers(disabledWithoutDocker = true)
class ProductMysqlIntegrationTest {

    private static final int THREADS = 8;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.jwks-uri", JwksTestServer::jwksUri);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private static String admin() {
        return "Bearer " + JwksTestServer.adminToken();
    }

    private static Map<String, Object> product(String name, String price) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "desc");
        body.put("price", new BigDecimal(price));
        body.put("category", "BOOKS");
        return body;
    }

    private JsonNode create(String name) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(product(name, "10.00"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private List<Integer> runConcurrently(Callable<Integer> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void migrationsMatchEntities_andCrudWorks() throws Exception {
        // reaching this point means Flyway V1..V2 ran and hibernate validate accepted the schema
        String name = "mysql-" + UUID.randomUUID();
        JsonNode created = create(name);

        mvc.perform(get("/api/products/" + created.get("id").asLong()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products").param("q", name).param("sort", "price,asc"))
                .andExpect(status().isOk());
    }

    @Test
    void databaseCheckConstraint_rejectsNonPositivePrices_evenIfTheApiIsBypassed() {

        org.springframework.jdbc.UncategorizedSQLException exception =
                assertThrows(
                        org.springframework.jdbc.UncategorizedSQLException.class,
                        () -> jdbc.update(
                                "INSERT INTO products (name, price, category, status, version, created_at, updated_at) "
                                        + "VALUES ('zero-price', 0, 'BOOKS', 'ACTIVE', 0, NOW(6), NOW(6))"
                        )
                );

        assertTrue(
                exception.getSQLException().getMessage().toLowerCase().contains("check")
                        || exception.getSQLException().getMessage().toLowerCase().contains("constraint"),
                "Expected MySQL CHECK constraint violation"
        );
    }

    @Test
    void concurrentUpdatesWithTheSameVersion_exactlyOneWins_othersGet409() throws Exception {
        JsonNode created = create("race-" + UUID.randomUUID());
        long id = created.get("id").asLong();

        List<Integer> statuses = runConcurrently(() -> {
            Map<String, Object> body = product("winner-" + UUID.randomUUID(), "11.00");
            body.put("version", created.get("version").asLong());
            return mvc.perform(put("/api/products/" + id)
                            .header(HttpHeaders.AUTHORIZATION, admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andReturn().getResponse().getStatus();
        });

        long ok = statuses.stream().filter(s -> s == 200).count();
        long conflicts = statuses.stream().filter(s -> s == 409).count();
        assertEquals(1, ok, "statuses: " + statuses);
        assertEquals(THREADS - 1, conflicts, "statuses: " + statuses);
        assertTrue(statuses.stream().noneMatch(s -> s >= 500), "no server errors expected: " + statuses);
    }
}
