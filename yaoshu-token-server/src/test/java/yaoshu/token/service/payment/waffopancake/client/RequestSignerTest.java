package yaoshu.token.service.payment.waffopancake.client;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * RequestSigner 单测 —— 覆盖 RFC §1.2 canonicalRequest 四段式签名算法。
 * <p>
 * 验证策略：
 * 1. SHA256_BASE64 用 JDK MessageDigest 独立计算对比（不用被测代码自己算的值）
 * 2. canonicalRequest 拼装格式（METHOD\nPATH\nTIMESTAMP\nSHA256_BASE64(BODY)）
 *    通过"公钥验证签名"间接验证 —— 手动构造预期 canonicalRequest，用公钥验证签名是否有效
 */
@DisplayName("RequestSigner — canonicalRequest 四段式签名")
class RequestSignerTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    // ======================== sha256Base64 ========================

    @Test
    @DisplayName("空 body 的 SHA256_BASE64 与 JDK MessageDigest 独立计算一致")
    void sha256Base64_EmptyBody_MatchesJdkDigest() throws Exception {
        byte[] body = new byte[0];
        String actual = RequestSigner.sha256Base64(body);
        String expected = computeSha256Base64(body);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("非空 body 的 SHA256_BASE64 与 JDK MessageDigest 独立计算一致")
    void sha256Base64_NonEmptyBody_MatchesJdkDigest() throws Exception {
        byte[] body = "{\"productId\":\"PROD_123\"}".getBytes(StandardCharsets.UTF_8);
        String actual = RequestSigner.sha256Base64(body);
        String expected = computeSha256Base64(body);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("null body 按空数组处理（GET 请求场景）")
    void sha256Base64_NullBody_TreatedAsEmpty() throws Exception {
        String actual = RequestSigner.sha256Base64(null);
        String expected = computeSha256Base64(new byte[0]);
        assertEquals(expected, actual);
    }

    // ======================== sign（canonicalRequest 四段式） ========================

    @Test
    @DisplayName("签名符合 canonicalRequest 四段式拼装（公钥验证签名有效）")
    void sign_SignatureMatchesCanonicalRequestFourSegments() throws Exception {
        String method = "POST";
        String path = "/v1/actions/auth/issue-session-token";
        String timestamp = "1718900000";
        byte[] body = "{\"productId\":\"PROD_123\",\"buyerIdentity\":\"yaoshu-user-1\"}"
                .getBytes(StandardCharsets.UTF_8);

        String signatureBase64 = RequestSigner.sign(method, path, body, timestamp, keyPair.getPrivate());
        assertNotNull(signatureBase64);

        // 手动构造预期 canonicalRequest（四段式：METHOD\nPATH\nTIMESTAMP\nSHA256_BASE64(BODY)）
        String bodyHash = RequestSigner.sha256Base64(body);
        String expectedCanonical = method + "\n" + path + "\n" + timestamp + "\n" + bodyHash;

        // 用公钥验证签名是否是预期 canonicalRequest 的有效签名
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
        boolean valid = verifySignature(expectedCanonical.getBytes(StandardCharsets.UTF_8),
                signatureBytes, keyPair.getPublic());
        assertEquals(true, valid, "签名应匹配预期 canonicalRequest 四段式拼装");
    }

    @Test
    @DisplayName("GET 请求（空 body）签名四段式拼装正确")
    void sign_GetRequest_EmptyBody_CanonicalCorrect() throws Exception {
        String method = "GET";
        String path = "/v1/actions/stores";
        String timestamp = "1718900000";

        String signatureBase64 = RequestSigner.sign(method, path, new byte[0], timestamp, keyPair.getPrivate());

        // GET 请求 body hash = SHA256_BASE64(空)
        String bodyHash = RequestSigner.sha256Base64(new byte[0]);
        String expectedCanonical = method + "\n" + path + "\n" + timestamp + "\n" + bodyHash;

        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
        boolean valid = verifySignature(expectedCanonical.getBytes(StandardCharsets.UTF_8),
                signatureBytes, keyPair.getPublic());
        assertEquals(true, valid, "GET 请求签名应匹配四段式拼装");
    }

    @Test
    @DisplayName("相同输入两次签名结果一致（PKCS#1 v1.5 确定性签名）")
    void sign_DeterministicForSameInput() {
        String method = "POST";
        String path = "/v1/actions/checkout/create-session";
        String timestamp = "1718900000";
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        PrivateKey sk = keyPair.getPrivate();

        String sig1 = RequestSigner.sign(method, path, body, timestamp, sk);
        String sig2 = RequestSigner.sign(method, path, body, timestamp, sk);
        assertEquals(sig1, sig2, "SHA256withRSA (PKCS#1 v1.5) 应为确定性签名");
    }

    @Test
    @DisplayName("不同输入产生不同签名")
    void sign_DifferentInput_DifferentSignature() {
        PrivateKey sk = keyPair.getPrivate();
        String timestamp = "1718900000";

        String sig1 = RequestSigner.sign("POST", "/path-a", new byte[0], timestamp, sk);
        String sig2 = RequestSigner.sign("POST", "/path-b", new byte[0], timestamp, sk);
        assertNotEquals(sig1, sig2);
    }

    @Test
    @DisplayName("时间戳单位为秒级（API 请求签名契约，与 Webhook 毫秒级区分）")
    void sign_TimestampIsSecondsLevel() throws Exception {
        // 此用例为契约断言：API 请求签名 timestamp 由调用方传入，
        // RequestSigner 不做单位转换（保持透传）。验证传入秒级字符串原样进入 canonicalRequest。
        String secondsTimestamp = "1718900000";
        String signatureBase64 = RequestSigner.sign("POST", "/p", new byte[0], secondsTimestamp, keyPair.getPrivate());

        // 验证签名是用传入的秒级 timestamp 构造的 canonicalRequest
        String expectedCanonical = "POST\n/p\n" + secondsTimestamp + "\n" + RequestSigner.sha256Base64(new byte[0]);
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
        boolean valid = verifySignature(expectedCanonical.getBytes(StandardCharsets.UTF_8),
                signatureBytes, keyPair.getPublic());
        assertEquals(true, valid, "timestamp 应原样透传进入 canonicalRequest");
    }

    // ======================== 测试 helper ========================

    /** 独立 SHA-256 + Base64 计算（测试 oracle，不调用被测代码） */
    private static String computeSha256Base64(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data == null ? new byte[0] : data);
        return Base64.getEncoder().encodeToString(hash);
    }

    /** 用公钥验证 RSA-SHA256 签名（独立于 RequestSigner 的 sign 方法） */
    private static boolean verifySignature(byte[] canonicalBytes, byte[] signatureBytes, PublicKey publicKey)
            throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(canonicalBytes);
        return signature.verify(signatureBytes);
    }
}
