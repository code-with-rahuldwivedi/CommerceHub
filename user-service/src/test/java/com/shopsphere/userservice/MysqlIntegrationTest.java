package com.shopsphere.userservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.userservice.service.MailService;

/**
 * Runs the real Flyway migrations on a real MySQL and boots with hibernate ddl-auto=validate,
 * so any mismatch between entities and migrations fails here instead of in production.
 * Also hammers the race-prone paths. Skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // no "h2" profile: the datasource comes from the container
@Testcontainers(disabledWithoutDocker = true)
class MysqlIntegrationTest {

    private static final String PASSWORD = "Str0ng-Passw0rd!";
    private static final int THREADS = 8;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @MockitoBean private MailService mailService;

    private int postStatus(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andReturn().getResponse().getStatus();
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

    private static long count(List<Integer> statuses, int status) {
        return statuses.stream().filter(s -> s == status).count();
    }

    @Test
    void migrationsMatchEntities_andBasicFlowWorks() throws Exception {
        // reaching this point means Flyway V1..V6 ran and hibernate validate accepted the schema
        String email = "mysql-" + UUID.randomUUID() + "@example.com";

        assertEquals(201, postStatus("/api/auth/register", Map.of("name", "T", "email", email, "password", PASSWORD)));
        assertEquals(200, postStatus("/api/auth/login", Map.of("email", email, "password", PASSWORD)));
        assertEquals(401, postStatus("/api/auth/login", Map.of("email", email, "password", "wrong-password-1")));
    }

    @Test
    void concurrentRegistrationsOfTheSameEmail_createExactlyOneAccount() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";

        List<Integer> statuses = runConcurrently(
                () -> postStatus("/api/auth/register", Map.of("name", "T", "email", email, "password", PASSWORD)));

        assertEquals(1, count(statuses, 201), "statuses: " + statuses);
        assertEquals(THREADS - 1, count(statuses, 409), "statuses: " + statuses);
    }

    @Test
    void concurrentUseOfOneRefreshToken_succeedsExactlyOnce() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.com";
        postStatus("/api/auth/register", Map.of("name", "T", "email", email, "password", PASSWORD));
        JsonNode tokens = mapper.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String refreshToken = tokens.get("refreshToken").asText();

        List<Integer> statuses = runConcurrently(() -> postStatus("/api/auth/refresh", Map.of("refreshToken", refreshToken)));

        assertEquals(1, count(statuses, 200), "statuses: " + statuses);
        assertTrue(statuses.stream().noneMatch(s -> s >= 500), "no server errors expected, got: " + statuses);
    }

    @Test
    void concurrentFailedLogins_areAllCounted_andLockTheAccount() throws Exception {
        String email = "lock-" + UUID.randomUUID() + "@example.com";
        postStatus("/api/auth/register", Map.of("name", "T", "email", email, "password", PASSWORD));

        runConcurrently(() -> postStatus("/api/auth/login", Map.of("email", email, "password", "wrong-password-1")));

        // 8 failures against max-attempts=3 (test profile): the account must now be locked
        assertEquals(401, postStatus("/api/auth/login", Map.of("email", email, "password", PASSWORD)));
    }
}
