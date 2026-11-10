package io.github.geethapriya1625.workflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.geethapriya1625.workflow.config.TokenService;
import io.github.geethapriya1625.workflow.domain.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the full app against a real PostgreSQL (Testcontainers), with Flyway migrations and
 * Hibernate schema validation. Set TEST_DATABASE_URL to use an existing database instead.
 *
 * <p>One container for the whole run: a static singleton, deliberately not {@code @Container},
 * so Spring's cached test contexts never point at a stopped database.
 */
@SpringBootTest(properties = "app.outbox.relay-enabled=false")
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    private static final String EXTERNAL_DB = System.getenv("TEST_DATABASE_URL");
    private static final PostgreSQLContainer<?> POSTGRES = startUnlessExternal();

    private static PostgreSQLContainer<?> startUnlessExternal() {
        if (EXTERNAL_DB != null) {
            return null;
        }
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        return container;
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_DB);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "app"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "app"));
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TokenService tokens;

    @Autowired
    protected ObjectMapper json;

    /** Unique user ids keep tests independent while they share one database. */
    protected static String user(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected String bearer(String user, Role... roles) {
        return "Bearer " + tokens.issue(user, List.of(roles));
    }

    protected static String body(String title, String amount) {
        return """
                {"title": "%s", "category": "hardware", "amount": %s, "currency": "USD"}
                """.formatted(title, amount);
    }

    protected MvcResult submit(String user, String idempotencyKey, String body) throws Exception {
        return mvc.perform(post("/api/v1/requests")
                        .header("Authorization", bearer(user, Role.EMPLOYEE))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    protected String submitOk(String user, String amount) throws Exception {
        MvcResult res = submit(user, UUID.randomUUID().toString(), body("Request from " + user, amount));
        if (res.getResponse().getStatus() != 201) {
            throw new AssertionError("submit failed: " + res.getResponse().getContentAsString());
        }
        return read(res).get("id").asText();
    }

    protected MvcResult decide(String action, String id, String user, Role... roles) throws Exception {
        return mvc.perform(post("/api/v1/requests/{id}/{action}", id, action)
                        .header("Authorization", bearer(user, roles))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\": \"" + action + " by " + user + "\"}"))
                .andReturn();
    }

    protected MvcResult getAs(String path, String user, Role... roles) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(user, roles))).andReturn();
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
