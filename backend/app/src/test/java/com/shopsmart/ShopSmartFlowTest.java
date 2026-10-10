package com.shopsmart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check on a real PostgreSQL + pgvector (Testcontainers): migrations, seed data, auth, stock trigger,
 * checkout, cancel, recommender, grounded policy answers, agents and role protection.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "shopsmart.embeddings=hash",
        "spring.ai.model.embedding=none",
        "shopsmart.seed.demo-data=true",
        "shopsmart.seed.demo-users=true",
        "shopsmart.mock-shipping.enabled=false",
        "shopsmart.storage-dir=target/test-uploads",
        "shopsmart.rag.high=0.40", "shopsmart.rag.medium=0.30", "shopsmart.rag.min=0.20"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShopSmartFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired TestRestTemplate rest;
    @Autowired JdbcClient jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    // ----------------------------------------------------------------- helpers

    private String login(String email, String password) {
        ResponseEntity<JsonNode> r = rest.postForEntity("/api/auth/login", Map.of("email", email, "password", password), JsonNode.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        return r.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
    }

    private int available(String sku) {
        return jdbc.sql("SELECT available_qty FROM v_product_availability WHERE sku = :s").param("s", sku).query(Integer.class).single();
    }

    // ------------------------------------------------------------------- tests

    @Test @Order(1)
    void catalogSearchFilterAndCompare() {
        ResponseEntity<JsonNode> r = call(HttpMethod.GET, "/api/products?q=gaming laptop&sort=price_asc", null, null);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(r.getBody().get("items").size() > 0);
        assertEquals(108, call(HttpMethod.GET, "/api/products?size=60", null, null).getBody().get("total").asInt());
        ResponseEntity<JsonNode> cmp = call(HttpMethod.GET, "/api/products/compare?skus=LAP101,LAP102", null, null);
        assertEquals(2, cmp.getBody().get("products").size());
        assertTrue(cmp.getBody().get("specKeys").size() > 3);
    }

    @Test @Order(2)
    void protectedEndpointsNeedTheRightRole() {
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.GET, "/api/cart", null, null).getStatusCode());
        String shopper = login("basic@shopsmart.test", "Demo@1234");
        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, "/api/admin/audit", shopper, null).getStatusCode());
        String admin = login("admin@shopsmart.test", "Admin@123");
        assertEquals(HttpStatus.OK, call(HttpMethod.GET, "/api/admin/audit", admin, null).getStatusCode());
    }

    @Test @Order(3)
    void logoutRevokesAccessAndRefreshTokens() {
        ResponseEntity<JsonNode> session = rest.postForEntity("/api/auth/login",
                Map.of("email", "basic@shopsmart.test", "password", "Demo@1234"), JsonNode.class);
        assertEquals(HttpStatus.OK, session.getStatusCode());
        String accessToken = session.getBody().get("accessToken").asText();
        String refreshToken = session.getBody().get("refreshToken").asText();

        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.POST, "/api/auth/logout", null,
                Map.of("refreshToken", refreshToken)).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.GET, "/api/me", accessToken, null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, rest.postForEntity("/api/auth/refresh",
                Map.of("refreshToken", refreshToken), JsonNode.class).getStatusCode());
    }

    @Test @Order(4)
    void registrationCreatesAnAuthenticatedBasicAccount() {
        ResponseEntity<JsonNode> registered = rest.postForEntity("/api/auth/register", Map.of(
                "email", "new-customer@shopsmart.test",
                "password", "A-strong-test-password",
                "firstName", "New",
                "lastName", "Customer"), JsonNode.class);
        assertEquals(HttpStatus.CREATED, registered.getStatusCode());
        assertEquals("BASIC", registered.getBody().get("user").get("tier").asText());
        String token = registered.getBody().get("accessToken").asText();
        assertEquals(HttpStatus.OK, call(HttpMethod.GET, "/api/me", token, null).getStatusCode());

        ResponseEntity<JsonNode> selected = call(HttpMethod.PUT, "/api/me/tier", token, Map.of("tier", "PREMIUM"));
        assertEquals(HttpStatus.OK, selected.getStatusCode());
        assertEquals("PREMIUM", selected.getBody().get("tier").asText());
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.PUT, "/api/me/tier", token, Map.of("tier", "STAFF")).getStatusCode());
        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, "/api/cart/items", token, Map.of("sku", "LAP101", "quantity", 1)).getStatusCode());
        assertTrue(call(HttpMethod.GET, "/api/cart", token, null).getBody().get("discount").decimalValue().signum() > 0);
    }

    @Test @Order(5)
    void checkoutTakesStockAndCancelPutsItBack() {
        String token = login("prime@shopsmart.test", "Demo@1234");
        String sku = "HDP302";
        int before = available(sku);
        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, "/api/cart/items", token, Map.of("sku", sku, "quantity", 2)).getStatusCode());
        ResponseEntity<JsonNode> order = call(HttpMethod.POST, "/api/orders/checkout", token, Map.of());
        assertEquals(HttpStatus.CREATED, order.getStatusCode());
        assertEquals(before - 2, available(sku));
        String number = order.getBody().get("orderNumber").asText();
        ResponseEntity<JsonNode> cancelled = call(HttpMethod.POST, "/api/orders/" + number + "/cancel", token, null);
        assertEquals("CANCELLED", cancelled.getBody().get("status").asText());
        assertEquals(before, available(sku));
    }

    @Test @Order(6)
    void databaseRejectsOverselling() {
        long orderId = jdbc.sql("""
                INSERT INTO orders (order_number, user_id, subtotal, total, shipping_address)
                VALUES ('SSTEST0001', (SELECT id FROM users WHERE email = 'basic@shopsmart.test'), 1, 1, '{}'::jsonb) RETURNING id
                """).query(Long.class).single();
        Exception e = assertThrows(Exception.class, () -> jdbc.sql("""
                INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity)
                SELECT :o, id, name, price, 100000 FROM products WHERE sku = 'LAP101'
                """).param("o", orderId).update());
        assertTrue(String.valueOf(e.getMessage()).contains("INSUFFICIENT_STOCK") || e.getCause().getMessage().contains("INSUFFICIENT_STOCK"));
    }

    @Test @Order(7)
    void shippedOrdersCannotBeCancelled() {
        String token = login("basic@shopsmart.test", "Demo@1234");
        ResponseEntity<JsonNode> orders = call(HttpMethod.GET, "/api/orders", token, null);
        String shipped = null;
        for (JsonNode o : orders.getBody()) if ("SHIPPED".equals(o.get("status").asText())) shipped = o.get("orderNumber").asText();
        assertNotNull(shipped, "demo data should contain a shipped order");
        assertEquals(HttpStatus.CONFLICT, call(HttpMethod.POST, "/api/orders/" + shipped + "/cancel", token, null).getStatusCode());
    }

    @Test @Order(8)
    void recommenderReturnsInBudgetInStockProducts() {
        ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/assistant/recommend", null, Map.of("query", "I need a gaming laptop under ₹150000"));
        assertEquals(HttpStatus.OK, r.getStatusCode());
        JsonNode recs = r.getBody().get("recommendations");
        assertTrue(recs.size() > 0);
        for (JsonNode rec : recs) {
            assertTrue(rec.get("price").asDouble() <= 1000.0);
            assertFalse(rec.get("reason").asText().isBlank());
            assertNotNull(rec.get("productId"));
        }
        JsonNode vague = call(HttpMethod.POST, "/api/assistant/recommend", null, Map.of("query", "hello there")).getBody();
        assertTrue(vague.get("recommendations").isEmpty());
        assertFalse(vague.get("clarifyingQuestion").isNull());
    }

    @Test @Order(9)
    void policyAnswersAreGroundedAndUnknownQuestionsAreRefused() {
        JsonNode good = call(HttpMethod.POST, "/api/knowledge/ask", null, Map.of("question", "How many days do I have to return a laptop?")).getBody();
        assertTrue(good.get("sources").size() > 0, "answer should cite sources");
        assertTrue(good.get("answer").asText().contains("15"), "electronics return window is 15 days");
        JsonNode bad = call(HttpMethod.POST, "/api/knowledge/ask", null, Map.of("question", "What is the weather on Mars today?")).getBody();
        assertEquals(0, bad.get("sources").size());
        assertTrue(bad.get("followUpRequired").asBoolean());
    }

    @Test @Order(10)
    void chatRoutesOrderQuestionsToTheTrackingView() {
        String token = login("basic@shopsmart.test", "Demo@1234");
        JsonNode reply = call(HttpMethod.POST, "/api/assistant/chat", token, Map.of("message", "Where is my latest order?")).getBody();
        assertEquals("ORDER", reply.get("intent").asText());
        assertTrue(reply.get("payload").has("tracking"));
        JsonNode support = call(HttpMethod.POST, "/api/assistant/chat", token, Map.of("message", "My headphones arrived broken")).getBody();
        assertEquals("SUPPORT", support.get("intent").asText());
        assertTrue(support.get("payload").has("ticket"));
    }

    @Test @Order(11)
    void inventoryAgentRaisesAlertsAndWaitsForApproval() {
        String admin = login("admin@shopsmart.test", "Admin@123");
        JsonNode run = call(HttpMethod.POST, "/api/admin/agents/inventory/run", admin, null).getBody();
        assertEquals("AWAITING_APPROVAL", run.get("status").asText());
        assertTrue(run.get("steps").size() >= 3);
        long id = run.get("id").asLong();
        JsonNode done = call(HttpMethod.POST, "/api/admin/agents/runs/" + id + "/approve", admin, null).getBody();
        assertEquals("COMPLETED", done.get("status").asText());
        Integer open = jdbc.sql("SELECT count(*) FROM stock_alerts WHERE agent_run_id = :r").param("r", id).query(Integer.class).single();
        assertTrue(open > 0);
    }

    @Test @Order(12)
    void fulfilmentAgentFindsTheDelayedOrder() {
        String admin = login("admin@shopsmart.test", "Admin@123");
        JsonNode run = call(HttpMethod.POST, "/api/admin/agents/fulfillment/run", admin, null).getBody();
        assertEquals("COMPLETED", run.get("status").asText());
        Integer tickets = jdbc.sql("SELECT count(*) FROM support_tickets WHERE agent_run_id = :r AND team = 'LOGISTICS'")
                .param("r", run.get("id").asLong()).query(Integer.class).single();
        assertTrue(tickets >= 1);
    }

    @Test @Order(13)
    void everyAiAnswerLeavesAnAuditRecord() {
        Integer rag = jdbc.sql("SELECT count(*) FROM ai_decision_logs WHERE task_type = 'RAG_ANSWER'").query(Integer.class).single();
        Integer rec = jdbc.sql("SELECT count(*) FROM ai_decision_logs WHERE task_type = 'SHOPPING_RECOMMENDATION'").query(Integer.class).single();
        Integer agent = jdbc.sql("SELECT count(*) FROM ai_decision_logs WHERE task_type = 'AGENT_DECISION'").query(Integer.class).single();
        assertTrue(rag > 0 && rec > 0 && agent > 0);
    }
}
