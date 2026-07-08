package yaoshu.token.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ai.yue.library.base.convert.Convert;

import yaoshu.token.BaseIntegrationTest;

/**
 * 订阅扣费集成测试 — 复现 SDET UC-EE-123 场景，定位 Bug-BE-06 回归失败根因。
 * <p>
 * 验证链路：预置订阅数据 → 中转请求（mock 上游成功）→ 验证钱包不扣（从订阅额度扣）。
 * 使用 JDK 内置 HttpServer mock 上游，真实走完整计费链路。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubscriptionBillingIT extends BaseIntegrationTest {

    private static final String TEST_USER = "sub_billing_user";
    private static final String TEST_TOKEN_AUTH = "sk-testSubBillingKey32chars123456";
    private static final String TEST_MODEL = "test-sub-billing";
    private static final String SUB_TYPE = "subscription";

    private int testUserId;
    private int mockChannelId;
    private int subscriptionId;
    private int testPlanId;
    private HttpServer mockUpstream;

    @BeforeAll
    void setUpData() {
        try {
            mockUpstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            mockUpstream.createContext("/", this::handleMockUpstream);
            mockUpstream.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
            mockUpstream.start();
        } catch (Exception e) {
            throw new RuntimeException("启动 mock 上游失败", e);
        }

        long now = System.currentTimeMillis() / 1000;
        String mockBaseUrl = "http://127.0.0.1:" + mockUpstream.getAddress().getPort();

        // 创建测试用户（有钱包 quota）
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, `group`, quota) " +
                "VALUES (?, 'test', 'Sub Billing User', 2, 1, 'default', 5000000) " +
                "ON DUPLICATE KEY UPDATE quota = 5000000",
                TEST_USER);
        testUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, TEST_USER);

        // 创建 API Token（有足够 remain_quota）
        jdbcTemplate.update(
                "INSERT INTO tokens (user_id, `key`, status, name, created_time, remain_quota, " +
                "unlimited_quota, model_limits_enabled, `group`, cross_group_retry) " +
                "VALUES (?, ?, 1, 'sub-test-token', ?, 500000, 0, 0, 'default', 0) " +
                "ON DUPLICATE KEY UPDATE remain_quota = 500000, unlimited_quota = 0",
                testUserId, TEST_TOKEN_AUTH, now);

        // 创建 mock 上游渠道
        jdbcTemplate.update(
                "INSERT INTO channels (type, `key`, status, name, created_time, base_url, models, `group`, priority) " +
                "VALUES (1, 'sk-mock-sub-key', 1, 'mock-sub-channel', ?, '" + mockBaseUrl + "', ?, 'default', 1) " +
                "ON DUPLICATE KEY UPDATE base_url = '" + mockBaseUrl + "'",
                now, TEST_MODEL);
        mockChannelId = jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE name = 'mock-sub-channel'", Integer.class);

        // 创建渠道能力映射
        jdbcTemplate.update(
                "INSERT INTO abilities (`group`, model, channel_id, enabled, priority, weight) " +
                "VALUES ('default', ?, ?, 1, 1, 10) " +
                "ON DUPLICATE KEY UPDATE enabled = 1",
                TEST_MODEL, mockChannelId);

        // 创建订阅计划（用于验证 subscription_plan_id/subscription_plan_title 同步到消费日志）
        jdbcTemplate.update(
                "INSERT INTO subscription_plans (title, subtitle, price_amount, total_amount, enabled) " +
                "VALUES ('Test Sub Plan', 'IT verification', 10.0, 1000000, 1) " +
                "ON DUPLICATE KEY UPDATE title = 'Test Sub Plan', total_amount = 1000000");
        testPlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM subscription_plans WHERE title = 'Test Sub Plan'", Integer.class);

        // 预置订阅数据（复现 seedSubscription，plan_id 指向 testPlanId）
        jdbcTemplate.update(
                "INSERT INTO user_subscriptions (user_id, plan_id, amount_total, amount_used, " +
                "start_time, end_time, status, type, source, created_at, updated_at) " +
                "VALUES (?, ?, 1000000, 0, ?, ?, 'active', ?, 'test_seed', ?, ?) " +
                "ON DUPLICATE KEY UPDATE plan_id = ?, amount_total = 1000000, amount_used = 0, status = 'active'",
                testUserId, testPlanId, now, now + 86400, SUB_TYPE, now, now, testPlanId);
        subscriptionId = jdbcTemplate.queryForObject(
                "SELECT id FROM user_subscriptions WHERE user_id = ? AND type = ? AND status = 'active' ORDER BY id DESC LIMIT 1",
                Integer.class, testUserId, SUB_TYPE);

        // 清理可能存在的旧 preConsumeRecord
        jdbcTemplate.update("DELETE FROM subscription_pre_consume_records WHERE user_id = ?", testUserId);
    }

    @AfterAll
    void cleanData() {
        if (mockUpstream != null) mockUpstream.stop(0);
        jdbcTemplate.update("DELETE FROM subscription_pre_consume_records WHERE user_id = ?", testUserId);
        jdbcTemplate.update("DELETE FROM user_subscriptions WHERE id = ?", subscriptionId);
        jdbcTemplate.update("DELETE FROM subscription_plans WHERE id = ?", testPlanId);
        jdbcTemplate.update("DELETE FROM abilities WHERE channel_id = ?", mockChannelId);
        jdbcTemplate.update("DELETE FROM channels WHERE name = 'mock-sub-channel'");
        jdbcTemplate.update("DELETE FROM tokens WHERE `key` = ?", TEST_TOKEN_AUTH);
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_USER);
    }

    private void handleMockUpstream(HttpExchange exchange) throws java.io.IOException {
        try {
            String resp = "{\"id\":\"chatcmpl-mock\",\"object\":\"chat.completion\",\"created\":1700000000," +
                    "\"model\":\"mock\",\"choices\":[{\"index\":0,\"message\":" +
                    "{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]," +
                    "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }

    /**
     * 订阅用户中转请求 → 钱包不扣（从订阅额度扣）。
     * <p>
     * 这是 Bug-BE-06 UC-EE-123 的白盒复现：有活跃订阅的用户发起中转请求，
     * 计费应走 subscription_first 偏好从订阅额度扣，钱包 quota 不变。
     */
    @Test
    void relayWithSubscription_walletNotDeducted() {
        long walletBefore = getUserQuota(testUserId);
        long subUsedBefore = getSubscriptionAmountUsed(subscriptionId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", TEST_MODEL);
        body.put("messages", List.of(Map.of("role", "user", "content", "hello")));
        body.put("max_tokens", 50);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(TEST_TOKEN_AUTH);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                apiUrl("/v1/chat/completions"), HttpMethod.POST, entity, String.class);

        // 中转应成功
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("mock 上游成功响应应返回 2xx，实际: %s, body: %s", resp.getStatusCode(), resp.getBody())
                .isTrue();

        long walletAfter = getUserQuota(testUserId);
        long subUsedAfter = getSubscriptionAmountUsed(subscriptionId);

        // 核心断言：钱包不扣（从订阅额度扣）
        long walletDeduct = walletBefore - walletAfter;
        long subDeduct = subUsedAfter - subUsedBefore;

        // 输出关键诊断信息（即使断言失败也能看到链路状态）
        System.out.println("[SubBillingIT] walletBefore=" + walletBefore + " walletAfter=" + walletAfter +
                " walletDeduct=" + walletDeduct);
        System.out.println("[SubBillingIT] subUsedBefore=" + subUsedBefore + " subUsedAfter=" + subUsedAfter +
                " subDeduct=" + subDeduct);

        assertThat(walletDeduct)
                .as("订阅用户钱包应不扣（walletDeduct=0），实际扣减: %d", walletDeduct)
                .isEqualTo(0);
        assertThat(subDeduct)
                .as("订阅额度应扣减 > 0，实际: %d", subDeduct)
                .isGreaterThan(0);
    }

    /**
     * 验证 syncRelayInfo 修复（遗漏1）：订阅中转请求后消费日志应包含 billingSource='subscription'。
     * <p>
     * 修复前 BillingSessionService 未翻译 syncRelayInfo，billingSource 始终为 null，
     * 导致 RelayConsumeLogService 记录的 other.billingSource=null，TaskRelayService 异步任务拿不到资金来源。
     */
    @Test
    void relayWithSubscription_billingSourceSyncedToConsumeLog() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", TEST_MODEL);
        body.put("messages", List.of(Map.of("role", "user", "content", "syncRelayInfo verify")));
        body.put("max_tokens", 50);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(TEST_TOKEN_AUTH);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                apiUrl("/v1/chat/completions"), HttpMethod.POST, entity, String.class);

        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("中转请求应成功，实际: %s, body: %s", resp.getStatusCode(), resp.getBody())
                .isTrue();

        // 查询最新消费日志，验证 syncRelayInfo 同步的 billingSource
        String otherJson = jdbcTemplate.queryForObject(
                "SELECT other FROM logs WHERE user_id = ? AND type = 2 ORDER BY id DESC LIMIT 1",
                String.class, testUserId);

        assertThat(otherJson).as("消费日志 other 字段应存在").isNotNull();
        Map<String, Object> other = Convert.toJSONObject(otherJson);
        assertThat(other.get("billing_source"))
                .as("syncRelayInfo 应将 billing_source 同步为 'subscription'，修复前为 null")
                .isEqualTo("subscription");
    }

    /**
     * 验证 syncRelayInfo 订阅字段同步（遗漏1扩展）：订阅中转请求后消费日志 other 应包含
     * subscription_id / subscription_plan_id / subscription_plan_title / subscription_total 等字段。
     * <p>
     * 修复前 buildOther 完全遗漏了 Go appendBillingInfo 翻译，syncRelayInfo 设置的 subscription
     * 字段无法记录到消费日志，前端无法展示订阅计费详情。
     */
    @Test
    void relayWithSubscription_subscriptionFieldsSyncedToConsumeLog() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", TEST_MODEL);
        body.put("messages", List.of(Map.of("role", "user", "content", "subscription fields verify")));
        body.put("max_tokens", 50);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(TEST_TOKEN_AUTH);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                apiUrl("/v1/chat/completions"), HttpMethod.POST, entity, String.class);

        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("中转请求应成功，实际: %s, body: %s", resp.getStatusCode(), resp.getBody())
                .isTrue();

        String otherJson = jdbcTemplate.queryForObject(
                "SELECT other FROM logs WHERE user_id = ? AND type = 2 ORDER BY id DESC LIMIT 1",
                String.class, testUserId);

        assertThat(otherJson).as("消费日志 other 字段应存在").isNotNull();
        Map<String, Object> other = Convert.toJSONObject(otherJson);

        // 验证 subscription_id（syncRelayInfo 同步的订阅 ID）
        assertThat(other.get("subscription_id"))
                .as("syncRelayInfo 应将 subscription_id 同步到消费日志")
                .isEqualTo(subscriptionId);

        // 验证 subscription_plan_id（SubscriptionFunding.getPlanId → getPlanInfo 获取）
        assertThat(other.get("subscription_plan_id"))
                .as("syncRelayInfo 应将 subscription_plan_id 同步到消费日志")
                .isEqualTo(testPlanId);

        // 验证 subscription_plan_title
        assertThat(other.get("subscription_plan_title"))
                .as("syncRelayInfo 应将 subscription_plan_title 同步到消费日志")
                .isEqualTo("Test Sub Plan");

        // 验证 subscription_total（额度总额）
        assertThat(other.get("subscription_total"))
                .as("subscription_total 应记录订阅总额度")
                .isNotNull();

        // 验证 wallet_quota_deducted（订阅计费时钱包不扣）
        assertThat(other.get("wallet_quota_deducted"))
                .as("订阅计费时 wallet_quota_deducted 应为 0")
                .isEqualTo(0);
    }

    private long getUserQuota(int userId) {
        Long q = jdbcTemplate.queryForObject(
                "SELECT quota FROM users WHERE id = ?", Long.class, userId);
        return q != null ? q : 0;
    }

    private long getSubscriptionAmountUsed(int subId) {
        Long q = jdbcTemplate.queryForObject(
                "SELECT amount_used FROM user_subscriptions WHERE id = ?", Long.class, subId);
        return q != null ? q : 0;
    }
}
