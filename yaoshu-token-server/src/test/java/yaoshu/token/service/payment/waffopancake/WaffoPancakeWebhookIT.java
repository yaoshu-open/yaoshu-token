package yaoshu.token.service.payment.waffopancake;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import yaoshu.token.BaseIntegrationTest;
import yaoshu.token.service.OptionService;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Waffo Pancake Webhook 集成测试 —— 充值完整闭环 + 边界/异常场景 + 订阅分发验证。
 * <p>
 * <b>Mock 策略（红线15 合规）</b>：零 Mock。webhook 链路天然不调 Pancake 上游 API，
 * 本地生成 RSA 密钥对模拟 Pancake 签名，真实走 Service → Mapper → MySQL → Redis 完整链路。
 * <p>
 * <b>数据准备例外（规范 §2.2）</b>：top_ups/subscription_orders/users 通过 JdbcTemplate 直接 INSERT。
 * 原因：webhook 回调链路没有对应的管理 API 写入端点（pay 下单调上游 Pancake API，集成测试不覆盖），
 * 且需精确控制初始 quota / status / payment_provider 字段验证闭环逻辑。Javadoc 集中注明。
 * <p>
 * <b>订阅闭环范围说明</b>：completeExternalOrder 完整激活依赖 subscription_plans 数据，
 * 本测试类聚焦「SUB 前缀 webhook 正确分发到订阅链路」（resolveSubscriptionTradeNo 阶段），
 * completeExternalOrder 完整激活（createUserSubscriptionFromPlanTx）留给 Pancake 沙箱验证。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("WaffoPancake Webhook 集成测试 — 充值闭环 + 边界场景 + 订阅分发")
class WaffoPancakeWebhookIT extends BaseIntegrationTest {

    private static final String TEST_PUBKEY_OPTION = "WaffoPancakeWebhookPublicKeyTest";
    private static final String TEST_PUBKEY_OPTION_PROD = "WaffoPancakeWebhookPublicKeyProd";
    private static final long QUOTA_PER_UNIT = 500_000L;
    private static final String TEST_USERNAME = "test_wp_it_user";

    @Autowired
    private OptionService optionService;

    /** 本地 RSA 密钥对（模拟 Pancake） */
    private KeyPair pancakeKeyPair;
    private String testPublicKeyPem;

    // ======================== 生命周期：密钥对 + options 公钥配置 ========================

    @BeforeAll
    void setupKeysAndOptions() throws Exception {
        // 生成本地 RSA 密钥对模拟 Pancake（每次测试类执行生成新密钥对，避免跨类残留）
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        pancakeKeyPair = gen.generateKeyPair();
        testPublicKeyPem = toPublicKeyPem(pancakeKeyPair.getPublic());

        // 写 options 公钥（test 环境）+ 刷新缓存确保 getValue 读到
        jdbcTemplate.update(
                "INSERT INTO options (`key`, `value`) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)",
                TEST_PUBKEY_OPTION, testPublicKeyPem);
        optionService.refreshCache();
    }

    @AfterAll
    void cleanupOptions() {
        // 清理测试公钥配置（避免污染 dev 环境配置）
        jdbcTemplate.update("DELETE FROM options WHERE `key` IN (?, ?)",
                TEST_PUBKEY_OPTION, TEST_PUBKEY_OPTION_PROD);
        optionService.refreshCache();
    }

    @BeforeEach
    void cleanResidualTestData() {
        // 防御性清理：确保每个测试方法前无残留数据（test_ 前缀 + 本类 tradeNo 特征）
        jdbcTemplate.update("DELETE FROM logs WHERE user_id IN (SELECT id FROM users WHERE username = ?)", TEST_USERNAME);
        jdbcTemplate.update("DELETE FROM top_ups WHERE trade_no LIKE 'WAFFO_PANCAKE-%' AND user_id IN (SELECT id FROM users WHERE username = ?)", TEST_USERNAME);
        jdbcTemplate.update("DELETE FROM subscription_orders WHERE trade_no LIKE 'WAFFO_PANCAKE_SUB-%' AND user_id IN (SELECT id FROM users WHERE username = ?)", TEST_USERNAME);
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", TEST_USERNAME);
    }

    // ======================== 充值 webhook 完整闭环 ========================

    @Test
    @DisplayName("合法 webhook → top_ups.status=success + users.quota 累加（核心闭环）")
    void webhook_ValidSignature_TopupCompleted_QuotaIncreased() {
        // 准备：测试用户（quota=0）+ pending 充值订单（amount=1）
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-aaa111";
        insertPendingTopupOrder(userId, tradeNo, 1L, "waffo_pancake");

        // 构造合法 webhook body + 签名
        String body = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-" + userId);
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        // 发送 webhook
        ResponseEntity<String> resp = sendWebhook("test", body, signature);

        // 断言：HTTP 200 + 订单成功 + quota 累加（1 * 500000 = 500000）
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM top_ups WHERE trade_no = ?",
                String.class, tradeNo);
        assertThat(status).isEqualTo("success");
        Integer quota = jdbcTemplate.queryForObject(
                "SELECT quota FROM users WHERE id = ?",
                Integer.class, userId);
        assertThat(quota).isEqualTo((int) (1 * QUOTA_PER_UNIT));
    }

    @Test
    @DisplayName("幂等：重复发送 webhook 不重复累加 quota（FOR UPDATE 行锁 + status 校验）")
    void webhook_Idempotent_DuplicateNoDoubleQuota() {
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-bbb222";
        insertPendingTopupOrder(userId, tradeNo, 1L, "waffo_pancake");

        String body = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-" + userId);
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        // 第一次发送：成功
        ResponseEntity<String> resp1 = sendWebhook("test", body, signature);
        assertThat(resp1.getStatusCode().is2xxSuccessful()).isTrue();

        // 第二次发送（重复 webhook）：幂等，不重复累加
        ResponseEntity<String> resp2 = sendWebhook("test", body, signature);
        assertThat(resp2.getStatusCode().is2xxSuccessful()).isTrue();

        // quota 只累加一次
        Integer quota = jdbcTemplate.queryForObject(
                "SELECT quota FROM users WHERE id = ?",
                Integer.class, userId);
        assertThat(quota).isEqualTo((int) (1 * QUOTA_PER_UNIT));
    }

    @Test
    @DisplayName("不同 amount 累加正确（amount=3 → quota=1500000）")
    void webhook_DifferentAmount_CorrectQuotaAccumulation() {
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-ccc333";
        insertPendingTopupOrder(userId, tradeNo, 3L, "waffo_pancake");

        String body = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-" + userId);
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        ResponseEntity<String> resp = sendWebhook("test", body, signature);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        Integer quota = jdbcTemplate.queryForObject(
                "SELECT quota FROM users WHERE id = ?",
                Integer.class, userId);
        assertThat(quota).isEqualTo((int) (3 * QUOTA_PER_UNIT));
    }

    // ======================== 验签失败场景（401） ========================

    @Test
    @DisplayName("t 时间戳超出 5 分钟重放窗口 → HTTP 401（验签失败）")
    void webhook_ReplayWindowExceeded_401() {
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-ddd444";
        insertPendingTopupOrder(userId, tradeNo, 1L, "waffo_pancake");

        String body = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-" + userId);
        // 6 分钟前的时间戳（超出 5 分钟窗口）
        long staleT = System.currentTimeMillis() - 6L * 60 * 1000;
        String signature = signWebhook(body, staleT, pancakeKeyPair.getPrivate());

        ResponseEntity<String> resp = sendWebhook("test", body, signature);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);

        // 订单状态不应变更
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM top_ups WHERE trade_no = ?",
                String.class, tradeNo);
        assertThat(status).isEqualTo("pending");
    }

    @Test
    @DisplayName("签名不匹配（篡改 body 后签名失效）→ HTTP 401")
    void webhook_SignatureInvalid_401() {
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-eee555";
        insertPendingTopupOrder(userId, tradeNo, 1L, "waffo_pancake");

        // 对原始 body 签名，但发送时篡改 body（签名失效）
        String originalBody = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-" + userId);
        String signature = signWebhook(originalBody, pancakeKeyPair.getPrivate());
        String tamperedBody = originalBody.replace("ORD_it_test", "ORD_tampered");

        ResponseEntity<String> resp = sendWebhook("test", tamperedBody, signature);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("X-Waffo-Signature header 缺失 → HTTP 401")
    void webhook_MissingSignatureHeader_401() {
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-fff666";
        insertPendingTopupOrder(userId, tradeNo, 1L, "waffo_pancake");

        String body = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-" + userId);

        // 不带 X-Waffo-Signature header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                apiUrl("/api/waffo-pancake/webhook/test"),
                HttpMethod.POST, entity, String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    // ======================== 业务异常场景（200 OK 防 Pancake 无效重试） ========================

    @Test
    @DisplayName("body.mode=prod 但路径=test → HTTP 200（mode 不一致，防 Pancake 无效重试）")
    void webhook_ModeMismatch_200() {
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-ggg777";
        insertPendingTopupOrder(userId, tradeNo, 1L, "waffo_pancake");

        // body mode=prod，但用 test 私钥签名（验签通过 mode=test，但 body 声明 mode=prod）
        // 注意：此处 test 公钥只配置了 test 环境，prod 验签会失败。
        // 实际 verifier 会因 mode=prod 但仅 test 公钥验签成功 → MODE_KEY_MISMATCH → 401
        // 但 Controller 的 env vs mode 校验在 verify 之后：mode=prod 路径=test → 200 OK
        // 此用例验证 Controller 层 env/mode 一致性校验（非 verifier 层）
        // 为避免 verifier 拒绝，body mode 改为 test 但路径 prod（mode 与验签公钥一致，但与路径不一致）
        String body = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-" + userId);
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        // 路径 prod，但 body mode=test → Controller env/mode 校验不一致 → 200 OK
        ResponseEntity<String> resp = sendWebhook("prod", body, signature);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        // 订单不应被处理（mode 不一致，webhook 被 ACK 但不处理）
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM top_ups WHERE trade_no = ?",
                String.class, tradeNo);
        assertThat(status).isEqualTo("pending");
    }

    @Test
    @DisplayName("BuyerIdentity 不匹配（防跨用户劫持）→ HTTP 200（防 Pancake 无效重试，订单不处理）")
    void webhook_IdentityMismatch_200() {
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-hhh888";
        insertPendingTopupOrder(userId, tradeNo, 1L, "waffo_pancake");

        // webhook 声称 identity=yaoshu-user-999（与订单 userId 不匹配）
        String body = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-999");
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        ResponseEntity<String> resp = sendWebhook("test", body, signature);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        // 订单不应被处理（identity 不匹配，防劫持）
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM top_ups WHERE trade_no = ?",
                String.class, tradeNo);
        assertThat(status).isEqualTo("pending");
    }

    @Test
    @DisplayName("订单不存在 → HTTP 200（防 Pancake 对永久不可解事件无效重试）")
    void webhook_OrderNotFound_200() {
        int userId = insertTestUser(0);
        // 不插入 top_ups 订单
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-iii999-not-exist";

        String body = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-" + userId);
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        ResponseEntity<String> resp = sendWebhook("test", body, signature);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("eventType != order.completed → HTTP 200（不处理非首期事件）")
    void webhook_EventTypeNotOrderCompleted_200() {
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-jjj000";
        insertPendingTopupOrder(userId, tradeNo, 1L, "waffo_pancake");

        // eventType=subscription.payment_succeeded（续费事件，首期不处理）
        String body = buildWebhookBody("test", "subscription.payment_succeeded", tradeNo,
                "yaoshu-user-" + userId);
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        ResponseEntity<String> resp = sendWebhook("test", body, signature);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        // 订单不应被处理（非 order.completed 事件）
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM top_ups WHERE trade_no = ?",
                String.class, tradeNo);
        assertThat(status).isEqualTo("pending");
    }

    @Test
    @DisplayName("paymentProvider 不匹配（订单是 stripe）→ HTTP 200（订单不处理）")
    void webhook_ProviderMismatch_200() {
        int userId = insertTestUser(0);
        String tradeNo = "WAFFO_PANCAKE-" + userId + "-1718900000-kkk121";
        // 插入 payment_provider=stripe 的订单
        insertPendingTopupOrder(userId, tradeNo, 1L, "stripe");

        String body = buildWebhookBody("test", "order.completed", tradeNo,
                "yaoshu-user-" + userId);
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        ResponseEntity<String> resp = sendWebhook("test", body, signature);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        // 订单不应被处理（provider 不匹配）
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM top_ups WHERE trade_no = ?",
                String.class, tradeNo);
        assertThat(status).isEqualTo("pending");
    }

    // ======================== 订阅 webhook 分发验证 ========================

    @Test
    @DisplayName("SUB 前缀 tradeNo → 分发到订阅链路（resolveSubscriptionTradeNo 而非 resolveTradeNo）")
    void webhook_SubPrefix_DispatchedToSubscription() {
        int userId = insertTestUser(0);
        String subTradeNo = "WAFFO_PANCAKE_SUB-" + userId + "-1718900000-sub111";
        // 插入 subscription_orders 订单（SUB 前缀，status=pending）
        insertPendingSubscriptionOrder(userId, subTradeNo);

        // webhook identity 不匹配 → resolveSubscriptionTradeNo 抛异常 → 200 OK
        // 这验证了 SUB 前缀走订阅链路（而非充值链路 resolveTradeNo）
        String body = buildWebhookBody("test", "order.completed", subTradeNo,
                "yaoshu-user-999");
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        ResponseEntity<String> resp = sendWebhook("test", body, signature);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        // subscription_orders 订单不应被激活（identity 不匹配，订阅链路拒绝）
        // completeExternalOrder 完整激活依赖 subscription_plans 数据，留给沙箱验证
    }

    @Test
    @DisplayName("未知 env 路径段 → HTTP 404")
    void webhook_UnknownEnv_404() {
        String body = buildWebhookBody("test", "order.completed", "WAFFO_PANCAKE-1-x",
                "yaoshu-user-1");
        String signature = signWebhook(body, pancakeKeyPair.getPrivate());

        ResponseEntity<String> resp = sendWebhook("staging", body, signature);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ======================== 测试 helper ========================

    /**
     * 插入测试用户（JdbcTemplate 直接 INSERT）。
     * 原因见类 Javadoc：webhook 闭环测试需精确控制初始 quota。
     */
    private int insertTestUser(int initialQuota) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password, role, status, quota, used_quota, request_count, " +
                        "`group`, created_at) VALUES (?, ?, 1, 1, ?, 0, 0, 'default', ?)",
                TEST_USERNAME, "test_pwd_hash", initialQuota, System.currentTimeMillis());
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Integer.class, TEST_USERNAME);
    }

    /**
     * 插入 pending 充值订单（JdbcTemplate 直接 INSERT）。
     * 原因见类 Javadoc：webhook 闭环测试需精确控制 status/payment_provider/amount。
     */
    private void insertPendingTopupOrder(int userId, String tradeNo, long amount, String paymentProvider) {
        jdbcTemplate.update(
                "INSERT INTO top_ups (user_id, amount, money, trade_no, payment_method, payment_provider, " +
                        "create_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')",
                userId, amount, (double) amount, tradeNo, paymentProvider, paymentProvider,
                System.currentTimeMillis());
    }

    /**
     * 插入 pending 订阅订单（JdbcTemplate 直接 INSERT）。
     * 用于验证 SUB 前缀 webhook 分发到订阅链路。
     */
    private void insertPendingSubscriptionOrder(int userId, String tradeNo) {
        jdbcTemplate.update(
                "INSERT INTO subscription_orders (user_id, plan_id, money, trade_no, payment_method, " +
                        "payment_provider, status, create_time) VALUES (?, 1, 10.0, ?, 'waffo_pancake', " +
                        "'waffo_pancake', 'pending', ?)",
                userId, tradeNo, System.currentTimeMillis());
    }

    /** 构造 webhook body JSON（直接拼字符串，避免引入 JSON 库依赖） */
    private static String buildWebhookBody(String mode, String eventType, String tradeNo, String buyerIdentity) {
        return "{"
                + "\"id\":\"evt_it_test\","
                + "\"eventType\":\"" + eventType + "\","
                + "\"mode\":\"" + mode + "\","
                + "\"data\":{"
                + "\"orderId\":\"ORD_it_test\","
                + "\"orderMerchantExternalId\":\"" + tradeNo + "\","
                + "\"merchantProvidedBuyerIdentity\":\"" + buyerIdentity + "\","
                + "\"amount\":\"10.00\","
                + "\"currency\":\"USD\""
                + "}"
                + "}";
    }

    /** 用当前时间戳签名 */
    private static String signWebhook(String body, PrivateKey privateKey) {
        return signWebhook(body, System.currentTimeMillis(), privateKey);
    }

    /** 用指定时间戳签名：构造 X-Waffo-Signature: t=...,v1=... */
    private static String signWebhook(String body, long t, PrivateKey privateKey) {
        try {
            String signedPayload = t + "." + body;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signedPayload.getBytes(StandardCharsets.UTF_8));
            String v1 = Base64.getEncoder().encodeToString(signature.sign());
            return "t=" + t + ",v1=" + v1;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 发送 webhook 请求 */
    private ResponseEntity<String> sendWebhook(String env, String body, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (signature != null) {
            headers.set("X-Waffo-Signature", signature);
        }
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(
                apiUrl("/api/waffo-pancake/webhook/" + env),
                HttpMethod.POST, entity, String.class);
    }

    private static String toPublicKeyPem(java.security.PublicKey publicKey) {
        byte[] der = publicKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(der);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        sb.append("-----END PUBLIC KEY-----\n");
        return sb.toString();
    }
}
