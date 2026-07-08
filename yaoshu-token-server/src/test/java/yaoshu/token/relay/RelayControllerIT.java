package yaoshu.token.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

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
import yaoshu.token.config.ratio.GroupRatioConfig;
import yaoshu.token.config.ratio.ModelRatioConfig;

/**
 * RelayController 集成测试 —— 验证 dispatchRelay 的错误响应格式 + 计费编排（Bug-BE-02 白盒验证）。
 * <p>
 * 测试路径：
 * <ol>
 * <li>无可用渠道 → 503 错误格式验证</li>
 * <li>无效 Token → 401 错误格式验证</li>
 * <li>Claude 端点无渠道 → OpenAI 兼容 503</li>
 * <li>计费编排：成功转发 → 用户/Token quota 扣减验证（Bug-BE-02）</li>
 * <li>计费编排：免费模型（groupRatio=0）→ 跳过预扣费</li>
 * <li>计费编排：中转失败 → BillingSession.refund 返还预扣费</li>
 * </ol>
 * <p>
 * 计费测试使用 JDK 内置 HttpServer 模拟上游 AI API（第三方付费外部依赖，不可控），
 * 真实走完整链路：Filter → Controller → 计费编排（preConsume/settle/refund）→ 数据库扣减。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RelayControllerIT extends BaseIntegrationTest {

    // ======================== 错误响应测试数据 ========================

    private int testUserId;
    private static final String TEST_USER = "relay_test_user";
    /** DB 存储的原始 key（不含 sk- 前缀，存储时拼接 sk- 前缀作为完整 key） */
    private static final String TEST_TOKEN_KEY_DB = "testRelayRetryKey32characters1";
    /** Authorization Header 中使用的 Bearer Token（带 sk- 前缀，与 DB 存储 key 一致） */
    private static final String TEST_TOKEN_AUTH = "sk-" + TEST_TOKEN_KEY_DB;
    private static final String TEST_MODEL = "test-retry-model";

    // ======================== 计费编排测试数据（Bug-BE-02）========================

    /** 付费模型测试用户（default group，有限额度，验证扣减生效） */
    private static final String BILLING_USER = "relay_billing_user";
    private static final String BILLING_TOKEN_AUTH = "sk-testBillingKey32chars1234567890AB";
    private static final String BILLING_MODEL_OK = "test-billing-ok";
    private static final String BILLING_MODEL_FAIL = "test-billing-fail";

    /** 免费模型测试用户（groupRatio=0，验证跳过预扣费） */
    private static final String FREE_GROUP = "billing_free_test";
    private static final String FREE_USER = "relay_free_user";
    private static final String FREE_TOKEN_AUTH = "sk-testFreeKey32charsX12345678901234";
    private static final String FREE_MODEL = "test-billing-free";

    /** 预扣费失败测试用户（额度=0，验证预扣费异常不逃逸为 500 空 body） */
    private static final String INSUFFICIENT_USER = "relay_insuf_user";
    private static final String INSUFFICIENT_TOKEN_AUTH = "sk-testInsufficientKey32chars12345678";

    private int billingUserId;
    private int freeUserId;
    private int insufficientUserId;
    private int mockChannelId;
    private HttpServer mockUpstream;
    /** GroupRatioConfig 全局静态状态备份（@AfterAll 还原，避免污染其他测试） */
    private Map<String, Double> originalGroupRatios;

    // ======================== 数据部署 ========================

    @BeforeAll
    void setUpData() {
        startMockUpstream();
        deployErrorTestData();
        deployBillingTestData();
    }

    /** 启动 mock 上游 AI API（JDK 内置 HttpServer，模拟成功/失败响应） */
    private void startMockUpstream() {
        try {
            mockUpstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            // 捕获所有路径（上游请求路径为 /v1/chat/completions 等）
            mockUpstream.createContext("/", this::handleMockUpstream);
            // 多线程避免重试请求阻塞
            mockUpstream.setExecutor(Executors.newFixedThreadPool(4));
            mockUpstream.start();
        } catch (Exception e) {
            throw new RuntimeException("启动 mock 上游失败", e);
        }
    }

    /**
     * mock 上游响应处理器：
     * <ul>
     * <li>请求 model=test-billing-fail → 返回 500（触发重试耗尽 → refund 返还）</li>
     * <li>其他 → 返回 200 + 标准 OpenAI 兼容响应（含 usage，触发扣费结算）</li>
     * </ul>
     */
    private void handleMockUpstream(HttpExchange exchange) throws java.io.IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status;
            String resp;
            if (body.contains(BILLING_MODEL_FAIL)) {
                // 模拟上游错误（触发重试耗尽 → finally refund 返还预扣费）
                status = 500;
                resp = "{\"error\":{\"message\":\"mock upstream error\",\"type\":\"server_error\"}}";
            } else {
                // 模拟上游成功（标准 OpenAI 兼容响应，usage.prompt_tokens/completion_tokens > 0 触发实际扣费）
                status = 200;
                resp = "{\"id\":\"chatcmpl-mock\",\"object\":\"chat.completion\",\"created\":1700000000,"
                        + "\"model\":\"mock\",\"choices\":[{\"index\":0,\"message\":"
                        + "{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
            }
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } finally {
            exchange.close();
        }
    }

    /** 部署错误响应测试数据（无渠道场景，token 的 model 无对应 ability → DistributorFilter 选渠道失败） */
    private void deployErrorTestData() {
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, `group`) " +
                "VALUES ('" + TEST_USER + "', 'test', 'Test Relay User', 2, 1, 'default') " +
                "ON DUPLICATE KEY UPDATE username = username");
        testUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = '" + TEST_USER + "'", Integer.class);

        long now = System.currentTimeMillis() / 1000;
        jdbcTemplate.update(
                "INSERT INTO tokens (user_id, `key`, status, name, created_time, remain_quota, " +
                "unlimited_quota, model_limits_enabled, `group`, cross_group_retry) " +
                "VALUES (" + testUserId + ", '" + TEST_TOKEN_AUTH + "', 1, 'test-token', " + now + ", 1000000, 1, 0, 'default', 0) " +
                "ON DUPLICATE KEY UPDATE `key` = `key`");
    }

    /** 部署计费编排测试数据：付费/免费用户 + mock 渠道 + 能力映射 + 免费分组倍率 */
    private void deployBillingTestData() {
        long now = System.currentTimeMillis() / 1000;
        String mockBaseUrl = "http://127.0.0.1:" + mockUpstream.getAddress().getPort();

        // 付费测试用户（default group，有限额度，验证预扣费 + 结算扣减）
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, `group`, quota) " +
                "VALUES ('" + BILLING_USER + "', 'test', 'Billing Test User', 2, 1, 'default', 5000000) " +
                "ON DUPLICATE KEY UPDATE quota = 5000000");
        billingUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = '" + BILLING_USER + "'", Integer.class);
        jdbcTemplate.update(
                "INSERT INTO tokens (user_id, `key`, status, name, created_time, remain_quota, " +
                "unlimited_quota, model_limits_enabled, `group`, cross_group_retry) " +
                "VALUES (" + billingUserId + ", '" + BILLING_TOKEN_AUTH + "', 1, 'billing-token', " +
                now + ", 500000, 0, 0, 'default', 0) " +
                "ON DUPLICATE KEY UPDATE remain_quota = 500000, unlimited_quota = 0");

        // 免费测试用户（FREE_GROUP，groupRatio=0，验证跳过预扣费）
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, `group`, quota) " +
                "VALUES ('" + FREE_USER + "', 'test', 'Free Test User', 2, 1, '" + FREE_GROUP + "', 5000000) " +
                "ON DUPLICATE KEY UPDATE quota = 5000000");
        freeUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = '" + FREE_USER + "'", Integer.class);
        jdbcTemplate.update(
                "INSERT INTO tokens (user_id, `key`, status, name, created_time, remain_quota, " +
                "unlimited_quota, model_limits_enabled, `group`, cross_group_retry) " +
                "VALUES (" + freeUserId + ", '" + FREE_TOKEN_AUTH + "', 1, 'free-token', " +
                now + ", 500000, 0, 0, '" + FREE_GROUP + "', 0) " +
                "ON DUPLICATE KEY UPDATE remain_quota = 500000, unlimited_quota = 0");

        // 预扣费失败测试用户（default group，用户额度=0 但 Token 有额度，验证预扣费失败时返回 429 而非 500 空 body）
        // Token 需有正额度通过 TokenAuth 认证层，用户 quota=0 触发 preConsumeBilling 失败
        jdbcTemplate.update(
                "INSERT INTO users (username, password, display_name, role, status, `group`, quota) " +
                "VALUES ('" + INSUFFICIENT_USER + "', 'test', 'Insuf User', 2, 1, 'default', 0) " +
                "ON DUPLICATE KEY UPDATE quota = 0");
        insufficientUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = '" + INSUFFICIENT_USER + "'", Integer.class);
        jdbcTemplate.update(
                "INSERT INTO tokens (user_id, `key`, status, name, created_time, remain_quota, " +
                "unlimited_quota, model_limits_enabled, `group`, cross_group_retry) " +
                "VALUES (" + insufficientUserId + ", '" + INSUFFICIENT_TOKEN_AUTH + "', 1, 'insufficient-token', " +
                now + ", 500000, 0, 0, 'default', 0) " +
                "ON DUPLICATE KEY UPDATE remain_quota = 500000, unlimited_quota = 0");

        // mock 上游渠道（base_url 指向本地 HttpServer，支持全部计费测试模型）
        jdbcTemplate.update(
                "INSERT INTO channels (type, `key`, status, name, created_time, base_url, models, `group`, priority) " +
                "VALUES (1, 'sk-mock-upstream-key', 1, 'mock-billing-channel', " + now + ", '" + mockBaseUrl + "', " +
                "'" + BILLING_MODEL_OK + "," + BILLING_MODEL_FAIL + "," + FREE_MODEL + "', 'default', 1) " +
                "ON DUPLICATE KEY UPDATE base_url = '" + mockBaseUrl + "'");
        mockChannelId = jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE name = 'mock-billing-channel'", Integer.class);

        // 渠道能力映射：default group → 成功/失败模型；FREE_GROUP → 免费模型
        jdbcTemplate.update(
                "INSERT INTO abilities (`group`, model, channel_id, enabled, priority, weight) " +
                "VALUES ('default', '" + BILLING_MODEL_OK + "', " + mockChannelId + ", 1, 1, 10) " +
                "ON DUPLICATE KEY UPDATE enabled = 1");
        jdbcTemplate.update(
                "INSERT INTO abilities (`group`, model, channel_id, enabled, priority, weight) " +
                "VALUES ('default', '" + BILLING_MODEL_FAIL + "', " + mockChannelId + ", 1, 1, 10) " +
                "ON DUPLICATE KEY UPDATE enabled = 1");
        jdbcTemplate.update(
                "INSERT INTO abilities (`group`, model, channel_id, enabled, priority, weight) " +
                "VALUES ('" + FREE_GROUP + "', '" + FREE_MODEL + "', " + mockChannelId + ", 1, 1, 10) " +
                "ON DUPLICATE KEY UPDATE enabled = 1");

        // 配置免费分组倍率=0（GroupRatioConfig 是全局静态内存，测试后还原避免污染）
        originalGroupRatios = GroupRatioConfig.getGroupRatioCopy();
        Map<String, Double> withFree = new ConcurrentHashMap<>(originalGroupRatios);
        withFree.put(FREE_GROUP, 0.0);
        GroupRatioConfig.updateFromMaps(withFree, null, null);

        // 配置测试模型倍率（fail-fast 改造后，未配置倍率的模型会被拒绝；测试虚构模型需显式注册）
        ModelRatioConfig.putModelRatio(BILLING_MODEL_OK, 1.0);
        ModelRatioConfig.putModelRatio(BILLING_MODEL_FAIL, 1.0);
        ModelRatioConfig.putModelRatio(FREE_MODEL, 1.0);
    }

    @AfterAll
    void cleanData() {
        // 还原 GroupRatioConfig 全局状态
        if (originalGroupRatios != null) {
            GroupRatioConfig.updateFromMaps(originalGroupRatios, null, null);
        }
        // 清理测试模型倍率注册（避免污染全局 ModelRatioConfig）
        ModelRatioConfig.removeModelRatio(BILLING_MODEL_OK);
        ModelRatioConfig.removeModelRatio(BILLING_MODEL_FAIL);
        ModelRatioConfig.removeModelRatio(FREE_MODEL);
        // 关闭 mock 上游
        if (mockUpstream != null) {
            mockUpstream.stop(0);
        }
        // 清理计费测试数据
        if (mockChannelId > 0) {
            jdbcTemplate.execute("DELETE FROM abilities WHERE channel_id = " + mockChannelId);
        }
        jdbcTemplate.execute("DELETE FROM channels WHERE name = 'mock-billing-channel'");
        jdbcTemplate.execute("DELETE FROM tokens WHERE `key` IN ('" + BILLING_TOKEN_AUTH
                + "', '" + FREE_TOKEN_AUTH + "', '" + INSUFFICIENT_TOKEN_AUTH + "')");
        jdbcTemplate.execute("DELETE FROM users WHERE username IN ('" + BILLING_USER
                + "', '" + FREE_USER + "', '" + INSUFFICIENT_USER + "')");
        // 清理错误测试数据
        jdbcTemplate.execute("DELETE FROM tokens WHERE `key` = '" + TEST_TOKEN_AUTH + "'");
        jdbcTemplate.execute("DELETE FROM users WHERE username = '" + TEST_USER + "'");
    }

    // ======================== 错误响应测试用例 ========================

    /**
     * 无可用渠道 → 应返回 503 + OpenAI 兼容错误格式。
     * <p>
     * 验证链路：TokenAuth 通过 → DistributorFilter 选渠道失败 → 503 → writeApiError
     */
    @Test
    void relayNoAvailableChannel_returns503() {
        Map<String, Object> body = buildChatBody();
        ResponseEntity<String> resp = relayPost(body);

        assertThat(resp.getStatusCode().value()).isEqualTo(503);

        @SuppressWarnings("unchecked")
        Map<String, Object> errorResp = Convert.toJSONObject(resp.getBody());
        assertThat(errorResp).isNotNull();
        assertThat(errorResp).containsKey("error");

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) errorResp.get("error");
        assertThat(error).containsKey("message");
        assertThat(error).containsKey("type");
    }

    /**
     * 无效 Token → 应返回 4xx + OpenAI 兼容错误格式。
     */
    @Test
    void relayInvalidToken_returns4xx() {
        Map<String, Object> body = buildChatBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("sk-invalid-token-not-exist");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                apiUrl("/v1/chat/completions"), HttpMethod.POST, entity, String.class);

        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> errorResp = Convert.toJSONObject(resp.getBody());
        assertThat(errorResp).isNotNull();
        assertThat(errorResp).containsKey("error");
    }

    /**
     * Claude 端点 — 无可用渠道时返回 OpenAI 兼容 503（DistributorFilter 阶段未进入 Controller）。
     * Claude 格式错误仅在 dispatchRelay retry loop 内部失败时产生；
     * DistributorFilter 选渠道失败 → 503 + OpenAI 格式。
     */
    @Test
    void relayClaudeNoChannel_returnsOpenAI503() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", TEST_MODEL);
        body.put("max_tokens", 10);
        body.put("messages", java.util.List.of(Map.of("role", "user", "content", "hello")));

        ResponseEntity<String> resp = relayPost(body, "/v1/messages");

        assertThat(resp.getStatusCode().value()).isEqualTo(503);

        @SuppressWarnings("unchecked")
        Map<String, Object> respBody = Convert.toJSONObject(resp.getBody());
        // DistributorFilter 阶段错误 → OpenAI 格式 { "error": { "code":..., "message":..., "type":... } }
        assertThat(respBody).containsKey("error");
        assertThat(respBody.get("error")).isInstanceOf(Map.class);
    }

    // ======================== 计费编排测试用例（Bug-BE-02 白盒验证）========================

    /**
     * 场景1：成功转发 → 用户/Token quota 扣减验证。
     * <p>
     * 验证 Bug-BE-02 修复：dispatchRelay 计费编排（preConsume → 转发成功 → settle 结算扣减）。
     * 链路：PriceHelper.modelPriceHelper 构建 PriceData → preConsumeBilling 预扣 →
     * 上游 mock 200 → CompatibleHandler 解析 usage → settleBillingAndLog 结算 → quota 净扣减 > 0。
     */
    @Test
    void relaySuccess_billingDeducted() {
        long userQuotaBefore = getUserQuota(billingUserId);
        int tokenQuotaBefore = getTokenRemainQuota(BILLING_TOKEN_AUTH);

        Map<String, Object> body = buildBillingBody(BILLING_MODEL_OK);
        ResponseEntity<String> resp = relayPostWithToken(BILLING_TOKEN_AUTH, body);

        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("mock 上游成功响应应返回 2xx，实际: %s", resp.getStatusCode()).isTrue();

        long userQuotaAfter = getUserQuota(billingUserId);
        int tokenQuotaAfter = getTokenRemainQuota(BILLING_TOKEN_AUTH);

        // 核心断言：计费链路打通，用户 quota 与 Token remainQuota 均扣减 > 0
        assertThat(userQuotaBefore - userQuotaAfter)
                .as("用户 quota 应扣减 > 0（计费链路生效）").isGreaterThan(0);
        assertThat(tokenQuotaBefore - tokenQuotaAfter)
                .as("Token remainQuota 应扣减 > 0").isGreaterThan(0);
    }

    /**
     * 场景2：免费模型（groupRatio=0）→ 跳过预扣费。
     * <p>
     * 验证 dispatchRelay 计费编排中的免费模型分支：
     * PriceHelper 检测 groupRatio==0 → freeModel=true → preConsumeBilling 被跳过 → quota 不变。
     */
    @Test
    void relayFreeModel_skipsPreConsume() {
        long userQuotaBefore = getUserQuota(freeUserId);
        int tokenQuotaBefore = getTokenRemainQuota(FREE_TOKEN_AUTH);

        Map<String, Object> body = buildBillingBody(FREE_MODEL);
        ResponseEntity<String> resp = relayPostWithToken(FREE_TOKEN_AUTH, body);

        // 免费模型不预扣费，即使转发成功也不扣减（actualQuota=0，settle delta=0）
        long userQuotaAfter = getUserQuota(freeUserId);
        int tokenQuotaAfter = getTokenRemainQuota(FREE_TOKEN_AUTH);

        assertThat(userQuotaAfter)
                .as("免费模型（groupRatio=0）用户 quota 不应变").isEqualTo(userQuotaBefore);
        assertThat(tokenQuotaAfter)
                .as("免费模型 Token remainQuota 不应变").isEqualTo(tokenQuotaBefore);
    }

    /**
     * 场景3：中转失败 → BillingSession.refund 返还预扣费。
     * <p>
     * 验证 dispatchRelay try-finally 的失败返还逻辑：
     * preConsumeBilling 预扣 → 上游 mock 500 → 重试耗尽 → finally refund() 返还预扣费 → quota 恢复。
     */
    @Test
    void relayFailure_refundsPreConsume() {
        long userQuotaBefore = getUserQuota(billingUserId);
        int tokenQuotaBefore = getTokenRemainQuota(BILLING_TOKEN_AUTH);

        Map<String, Object> body = buildBillingBody(BILLING_MODEL_FAIL);
        ResponseEntity<String> resp = relayPostWithToken(BILLING_TOKEN_AUTH, body);

        // 重试耗尽后应返回错误状态码
        assertThat(resp.getStatusCode().isError())
                .as("上游持续 500 应返回错误状态，实际: %s", resp.getStatusCode()).isTrue();

        long userQuotaAfter = getUserQuota(billingUserId);
        int tokenQuotaAfter = getTokenRemainQuota(BILLING_TOKEN_AUTH);

        // 核心断言：失败返还 —— 预扣费被 refund 返还，quota 净变化=0（恢复至预扣前）
        assertThat(userQuotaAfter)
                .as("中转失败后用户 quota 应被返还（等于预扣前）").isEqualTo(userQuotaBefore);
        assertThat(tokenQuotaAfter)
                .as("中转失败后 Token remainQuota 应被返还").isEqualTo(tokenQuotaBefore);
    }

    /**
     * 预扣费失败（额度不足）→ 应返回 429 + OpenAI 兼容错误格式（非 500 空 body）。
     * <p>
     * 验证 Bug 修复：dispatchRelay 预扣费检查的 throw ex 曾位于 try 块之外，
     * 导致异常逃逸为 500 空 body。修复后通过 writeApiError 写入 OpenAI 兼容错误响应。
     * 链路：TokenAuth 通过 → DistributorFilter 选渠道成功 → dispatchRelay →
     * preConsumeBilling 返回错误 → writeApiError(429) → return。
     */
    @Test
    void relayInsufficientQuota_returns429WithErrorBody() {
        Map<String, Object> body = buildBillingBody(BILLING_MODEL_OK);
        ResponseEntity<String> resp = relayPostWithToken(INSUFFICIENT_TOKEN_AUTH, body);

        // 核心断言：429（非 500 空 body）
        assertThat(resp.getStatusCode().value())
                .as("预扣费失败应返回 429，实际: %s", resp.getStatusCode()).isEqualTo(429);
        assertThat(resp.getBody())
                .as("响应体不应为空（修复前为 content-length:0）").isNotNull().isNotEmpty();

        @SuppressWarnings("unchecked")
        Map<String, Object> errorResp = Convert.toJSONObject(resp.getBody());
        assertThat(errorResp).as("响应体应为合法 JSON").isNotNull();
        assertThat(errorResp).containsKey("error");

        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) errorResp.get("error");
        assertThat(error).containsKey("message");
        assertThat(error.get("message").toString()).isNotEmpty();
        assertThat(error).containsKey("code");
        assertThat(error.get("code")).isEqualTo("insufficient_user_quota");
    }

    // ======================== 辅助方法 ========================

    private Map<String, Object> buildChatBody() {
        return buildChatBody(TEST_MODEL);
    }

    private Map<String, Object> buildChatBody(String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", java.util.List.of(Map.of("role", "user", "content", "hello")));
        return body;
    }

    /**
     * 计费测试请求体（含 max_tokens，影响预扣费额度的 token 估算）。
     */
    private Map<String, Object> buildBillingBody(String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", "hello")));
        body.put("max_tokens", 50);
        return body;
    }

    private ResponseEntity<String> relayPost(Map<String, Object> body) {
        return relayPost(body, "/v1/chat/completions");
    }

    private ResponseEntity<String> relayPost(Map<String, Object> body, String path) {
        return relayPostWithToken(TEST_TOKEN_AUTH, body, path);
    }

    private ResponseEntity<String> relayPostWithToken(String bearerToken, Map<String, Object> body) {
        return relayPostWithToken(bearerToken, body, "/v1/chat/completions");
    }

    private ResponseEntity<String> relayPostWithToken(String bearerToken, Map<String, Object> body, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(apiUrl(path), HttpMethod.POST, entity, String.class);
    }

    /** 查询用户 quota（users.quota，BIGINT） */
    private long getUserQuota(int userId) {
        Long q = jdbcTemplate.queryForObject(
                "SELECT quota FROM users WHERE id = ?", Long.class, userId);
        return q != null ? q : 0;
    }

    /** 查询 Token remainQuota（tokens.remain_quota，INT） */
    private int getTokenRemainQuota(String tokenKey) {
        Integer q = jdbcTemplate.queryForObject(
                "SELECT remain_quota FROM tokens WHERE `key` = ?", Integer.class, tokenKey);
        return q != null ? q : 0;
    }
}
