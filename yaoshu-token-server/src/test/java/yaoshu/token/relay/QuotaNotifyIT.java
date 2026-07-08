package yaoshu.token.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

import yaoshu.token.BaseIntegrationTest;

/**
 * 额度通知触发集成测试 — 验证 Session-39 第二轮修复的 checkAndSendQuotaNotify 翻译。
 * <p>
 * 验证链路：用户额度低于阈值 → 中转请求结算 → BillingService.checkAndSendQuotaNotify 触发 →
 * UserNotifyService 通过 webhook 发送通知到本地 mock server。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuotaNotifyIT extends BaseIntegrationTest {

    private static final String TEST_USER = "quota_notify_user";
    private static final String TEST_TOKEN_AUTH = "sk-testQuotaNotifyKey32chars12";
    private static final String TEST_MODEL = "test-notify-billing";

    private int testUserId;
    private int mockChannelId;
    private HttpServer mockUpstream;
    private HttpServer mockWebhook;
    private final AtomicInteger webhookReceived = new AtomicInteger(0);

    @BeforeAll
    void setUpData() {
        try {
            // mock 上游
            mockUpstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            mockUpstream.createContext("/", this::handleMockUpstream);
            mockUpstream.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
            mockUpstream.start();

            // mock webhook 通知接收端
            mockWebhook = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            mockWebhook.createContext("/notify", this::handleWebhook);
            mockWebhook.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
            mockWebhook.start();
        } catch (Exception e) {
            throw new RuntimeException("启动 mock server 失败", e);
        }

        long now = System.currentTimeMillis() / 1000;
        String mockBaseUrl = "http://127.0.0.1:" + mockUpstream.getAddress().getPort();
        String webhookUrl = "http://127.0.0.1:" + mockWebhook.getAddress().getPort() + "/notify";

        // 创建测试用户：钱包额度充足但低于通知阈值（阈值设为极大值确保触发）
        // setting 配置 webhook 通知 + 极高阈值
        String settingJson = "{\"notifyType\":\"webhook\",\"quotaWarningThreshold\":999999999,"
                + "\"webhookUrl\":\"" + webhookUrl + "\"}";
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, `group`, quota, setting) " +
                "VALUES (?, 'test', 'Notify User', 2, 1, 'default', 5000000, ?) " +
                "ON DUPLICATE KEY UPDATE quota = 5000000, setting = ?",
                TEST_USER, settingJson, settingJson);
        testUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Integer.class, TEST_USER);

        // 清除可能存在的 Redis 通知限流缓存 key
        try {
            jdbcTemplate.update("DELETE FROM tokens WHERE `key` = ?", TEST_TOKEN_AUTH);
        } catch (Exception ignored) { }

        // 创建 API Token
        jdbcTemplate.update(
                "INSERT INTO tokens (user_id, `key`, status, name, created_time, remain_quota, " +
                "unlimited_quota, model_limits_enabled, `group`) " +
                "VALUES (?, ?, 1, 'notify-test-token', ?, 500000, 0, 0, 'default')",
                testUserId, TEST_TOKEN_AUTH, now);

        // 创建 mock 上游渠道
        jdbcTemplate.update(
                "INSERT INTO channels (type, `key`, status, name, created_time, base_url, models, `group`, priority) " +
                "VALUES (1, 'sk-mock-notify-key', 1, 'mock-notify-channel', ?, '" + mockBaseUrl + "', ?, 'default', 1) " +
                "ON DUPLICATE KEY UPDATE base_url = '" + mockBaseUrl + "'",
                now, TEST_MODEL);
        mockChannelId = jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE name = 'mock-notify-channel'", Integer.class);

        // 创建渠道能力映射
        jdbcTemplate.update(
                "INSERT INTO abilities (`group`, model, channel_id, enabled, priority, weight) " +
                "VALUES ('default', ?, ?, 1, 1, 10) " +
                "ON DUPLICATE KEY UPDATE enabled = 1",
                TEST_MODEL, mockChannelId);

        webhookReceived.set(0);
    }

    @AfterAll
    void cleanData() {
        if (mockUpstream != null) mockUpstream.stop(0);
        if (mockWebhook != null) mockWebhook.stop(0);
        jdbcTemplate.update("DELETE FROM abilities WHERE channel_id = ?", mockChannelId);
        jdbcTemplate.update("DELETE FROM channels WHERE name = 'mock-notify-channel'");
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

    private void handleWebhook(HttpExchange exchange) throws java.io.IOException {
        try {
            webhookReceived.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
        } finally {
            exchange.close();
        }
    }

    /**
     * 钱包用户额度低于阈值时，结算后触发 webhook 通知。
     * <p>
     * 验证 Session-39 第二轮修复：checkAndSendQuotaNotify（对应 Go quota.go）
     * 在结算后检查 user.quota < quotaWarningThreshold 时通过 UserNotifyService 发送通知。
     */
    @Test
    void walletQuotaBelowThreshold_triggersWebhookNotify() throws InterruptedException {
        webhookReceived.set(0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", TEST_MODEL);
        body.put("messages", List.of(Map.of("role", "user", "content", "notify test")));
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

        // 通知发送是同步的（在 settleBilling 中），但 webhook HTTP 调用可能有微小延迟
        Thread.sleep(500);

        int received = webhookReceived.get();
        assertThat(received)
                .as("额度低于阈值时应触发 webhook 通知（expected >= 1, actual: %d）", received)
                .isGreaterThanOrEqualTo(1);
    }
}
