package yaoshu.token.service.payment.waffopancake.client;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeWebhookEvent;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;

/**
 * WaffoPancakeWebhookVerifier 单测 —— 覆盖 RFC §1.4 验签算法全部分支。
 * <p>
 * Mock 策略：OptionService（公钥来源）是验签的辅助依赖，单测 Mock 它聚焦验签算法本身，
 * 符合红线15「Mock 仅限外部不可控依赖」边界——验签的核心是 RSA-SHA256 算法，
 * 公钥从 options 表读是运行时配置行为，集成测试（WaffoPancakeWebhookIT）真实连接验证。
 * <p>
 * 使用 LENIENT strictness：部分用例（t 超窗/header 格式错误）在调用 getValue 前抛异常，
 * 统一 stub 两个公钥避免 UnnecessaryStubbingException。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WaffoPancakeWebhookVerifier — Webhook 验签算法")
class WaffoPancakeWebhookVerifierTest {

    private static final String TEST_PUBKEY_OPTION = "WaffoPancakeWebhookPublicKeyTest";
    private static final String PROD_PUBKEY_OPTION = "WaffoPancakeWebhookPublicKeyProd";

    private static KeyPair testKeyPair;
    private static KeyPair prodKeyPair;
    private static KeyPair attackerKeyPair;  // 第三套密钥（既非 test 也非 prod）用于签名不匹配场景
    private static String testPubPem;
    private static String prodPubPem;

    @Mock
    private OptionService optionService;

    private WaffoPancakeWebhookVerifier verifier;

    @BeforeAll
    static void generateKeyPairs() throws Exception {
        testKeyPair = generateRsaKeyPair();
        prodKeyPair = generateRsaKeyPair();
        attackerKeyPair = generateRsaKeyPair();
        testPubPem = toPublicKeyPem(testKeyPair.getPublic());
        prodPubPem = toPublicKeyPem(prodKeyPair.getPublic());
    }

    @BeforeEach
    void setUp() {
        // lenient：部分用例不会触达 getValue（如 header 格式错误在验签前抛异常）
        lenient().when(optionService.getValue(TEST_PUBKEY_OPTION)).thenReturn(testPubPem);
        lenient().when(optionService.getValue(PROD_PUBKEY_OPTION)).thenReturn(prodPubPem);
        verifier = new WaffoPancakeWebhookVerifier(optionService);
    }

    // ======================== 合法验签通过 ========================

    @Test
    @DisplayName("合法签名（test 公钥）+ mode=test 验签通过")
    void verify_ValidSignature_TestMode_Success() {
        String body = buildWebhookBody("test", "WAFFO_PANCAKE-1-1718900000-abc123", "yaoshu-user-1");
        String header = signHeader(body, testKeyPair.getPrivate());

        WaffoPancakeWebhookEvent event = verifier.verify(body, header);

        assertNotNull(event);
        assertEquals("test", event.getMode());
        assertEquals("order.completed", event.getEventType());
        assertEquals("WAFFO_PANCAKE-1-1718900000-abc123", event.getData().getOrderMerchantExternalId());
    }

    @Test
    @DisplayName("合法签名（prod 公钥）+ mode=prod 验签通过")
    void verify_ValidSignature_ProdMode_Success() {
        String body = buildWebhookBody("prod", "WAFFO_PANCAKE-2-1718900000-xyz789", "yaoshu-user-2");
        String header = signHeader(body, prodKeyPair.getPrivate());

        WaffoPancakeWebhookEvent event = verifier.verify(body, header);

        assertNotNull(event);
        assertEquals("prod", event.getMode());
    }

    // ======================== 重放防护 ========================

    @Test
    @DisplayName("t 超出 5 分钟重放窗口拒绝（REPLAY_WINDOW_EXCEEDED）")
    void verify_ReplayWindowExceeded_Throws() {
        String body = buildWebhookBody("test", "WAFFO_PANCAKE-1-1718900000-abc", "yaoshu-user-1");
        // 构造 6 分钟前的时间戳（超出 5 分钟窗口）
        long staleT = System.currentTimeMillis() - 6L * 60 * 1000;
        String header = signHeader(body, staleT, testKeyPair.getPrivate());

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.REPLAY_WINDOW_EXCEEDED, ex.getReason());
    }

    @Test
    @DisplayName("未来时间戳超出 5 分钟重放窗口拒绝")
    void verify_FutureTimestampExceeded_Throws() {
        String body = buildWebhookBody("test", "WAFFO_PANCAKE-1-x", "yaoshu-user-1");
        long futureT = System.currentTimeMillis() + 6L * 60 * 1000;
        String header = signHeader(body, futureT, testKeyPair.getPrivate());

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.REPLAY_WINDOW_EXCEEDED, ex.getReason());
    }

    // ======================== 签名不匹配 ========================

    @Test
    @DisplayName("签名不匹配（attacker 私钥签名，test/prod 公钥都验签失败）拒绝（SIGNATURE_INVALID）")
    void verify_SignatureMismatch_Throws() {
        String body = buildWebhookBody("test", "WAFFO_PANCAKE-1-x", "yaoshu-user-1");
        // 用第三套密钥签名（既非 test 也非 prod）
        String header = signHeader(body, attackerKeyPair.getPrivate());

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.SIGNATURE_INVALID, ex.getReason());
    }

    // ======================== mode 与公钥不一致 ========================

    @Test
    @DisplayName("mode=test 但仅 prod 公钥验签成功拒绝（MODE_KEY_MISMATCH，防公钥环境错位）")
    void verify_ModeKeyMismatch_TestModeProdKey_Throws() {
        // body 声明 mode=test，但用 prod 私钥签名（test 验签失败，prod 验签成功）
        String body = buildWebhookBody("test", "WAFFO_PANCAKE-1-x", "yaoshu-user-1");
        String header = signHeader(body, prodKeyPair.getPrivate());

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.MODE_KEY_MISMATCH, ex.getReason());
    }

    @Test
    @DisplayName("mode=prod 但仅 test 公钥验签成功拒绝（MODE_KEY_MISMATCH）")
    void verify_ModeKeyMismatch_ProdModeTestKey_Throws() {
        String body = buildWebhookBody("prod", "WAFFO_PANCAKE-1-x", "yaoshu-user-1");
        String header = signHeader(body, testKeyPair.getPrivate());

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.MODE_KEY_MISMATCH, ex.getReason());
    }

    // ======================== header 格式错误 ========================

    @Test
    @DisplayName("header 缺 t 字段拒绝（MALFORMED_SIGNATURE_HEADER）")
    void verify_HeaderMissingT_Throws() {
        String body = buildWebhookBody("test", "WAFFO_PANCAKE-1-x", "yaoshu-user-1");
        String header = "v1=" + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER, ex.getReason());
    }

    @Test
    @DisplayName("header 缺 v1 字段拒绝（MALFORMED_SIGNATURE_HEADER）")
    void verify_HeaderMissingV1_Throws() {
        String body = buildWebhookBody("test", "WAFFO_PANCAKE-1-x", "yaoshu-user-1");
        String header = "t=" + System.currentTimeMillis();

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER, ex.getReason());
    }

    @Test
    @DisplayName("空白 header 拒绝（MALFORMED_SIGNATURE_HEADER）")
    void verify_BlankHeader_Throws() {
        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify("body", ""));
        assertEquals(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER, ex.getReason());
    }

    @Test
    @DisplayName("v1 非合法 Base64 拒绝（MALFORMED_SIGNATURE_HEADER）")
    void verify_InvalidBase64Signature_Throws() {
        String body = buildWebhookBody("test", "WAFFO_PANCAKE-1-x", "yaoshu-user-1");
        long t = System.currentTimeMillis();
        // v1 含非法 Base64 字符（!, 空格等）
        String header = "t=" + t + ",v1=!!!not-base64!!!";

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER, ex.getReason());
    }

    // ======================== body 异常 ========================

    @Test
    @DisplayName("raw body 为 null 拒绝（MALFORMED_SIGNATURE_HEADER）")
    void verify_NullBody_Throws() {
        String header = signHeader("dummy", testKeyPair.getPrivate());
        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(null, header));
        assertEquals(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER, ex.getReason());
    }

    @Test
    @DisplayName("body 缺 mode 字段拒绝（验签通过后，MALFORMED_SIGNATURE_HEADER）")
    void verify_MissingModeField_Throws() {
        // body 无 mode 字段，但用 test 私钥签名（验签会通过，但 mode 校验失败）
        String body = "{\"id\":\"evt_1\",\"eventType\":\"order.completed\","
                + "\"data\":{\"orderMerchantExternalId\":\"WAFFO_PANCAKE-1-x\","
                + "\"merchantProvidedBuyerIdentity\":\"yaoshu-user-1\"}}";
        String header = signHeader(body, testKeyPair.getPrivate());

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER, ex.getReason());
    }

    @Test
    @DisplayName("mode 值非 test/prod 拒绝（MALFORMED_SIGNATURE_HEADER）")
    void verify_InvalidModeValue_Throws() {
        String body = buildWebhookBody("staging", "WAFFO_PANCAKE-1-x", "yaoshu-user-1");
        String header = signHeader(body, testKeyPair.getPrivate());

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER, ex.getReason());
    }

    @Test
    @DisplayName("公钥均未配置时拒绝（SIGNATURE_INVALID，公钥 PEM 为空跳过验签）")
    void verify_BothKeysUnconfigured_Throws() {
        // 覆盖公钥未配置场景：两个 getValue 都返回 null
        lenient().when(optionService.getValue(TEST_PUBKEY_OPTION)).thenReturn(null);
        lenient().when(optionService.getValue(PROD_PUBKEY_OPTION)).thenReturn(null);

        String body = buildWebhookBody("test", "WAFFO_PANCAKE-1-x", "yaoshu-user-1");
        String header = signHeader(body, testKeyPair.getPrivate());

        WaffoPancakeWebhookException ex = assertThrows(WaffoPancakeWebhookException.class,
                () -> verifier.verify(body, header));
        assertEquals(WaffoPancakeWebhookException.Reason.SIGNATURE_INVALID, ex.getReason());
    }

    // ======================== 测试 helper ========================

    /** 构造 webhook body JSON（fastjson2 不引入，直接拼字符串保持测试零额外依赖） */
    private static String buildWebhookBody(String mode, String tradeNo, String buyerIdentity) {
        return "{"
                + "\"id\":\"evt_test_1\","
                + "\"eventType\":\"order.completed\","
                + "\"mode\":\"" + mode + "\","
                + "\"data\":{"
                + "\"orderId\":\"ORD_test_1\","
                + "\"orderMerchantExternalId\":\"" + tradeNo + "\","
                + "\"merchantProvidedBuyerIdentity\":\"" + buyerIdentity + "\","
                + "\"amount\":\"10.00\","
                + "\"currency\":\"USD\""
                + "}"
                + "}";
    }

    /** 用当前时间戳签名 */
    private static String signHeader(String body, PrivateKey privateKey) {
        return signHeader(body, System.currentTimeMillis(), privateKey);
    }

    /** 用指定时间戳签名：构造 X-Waffo-Signature: t=...,v1=... */
    private static String signHeader(String body, long t, PrivateKey privateKey) {
        try {
            String signedPayload = t + "." + body;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signedPayload.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            String v1 = Base64.getEncoder().encodeToString(signed);
            return "t=" + t + ",v1=" + v1;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static String toPublicKeyPem(PublicKey publicKey) {
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
