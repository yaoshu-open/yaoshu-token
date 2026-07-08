package yaoshu.token.service.payment.waffopancake.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Waffo Pancake API 请求签名器（RFC §1.2 canonicalRequest 四段式）。
 * <p>
 * 算法（权威定义：https://docs.waffo.ai/api-reference/authentication）：
 * <pre>
 * 1. canonicalRequest = METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + SHA256_BASE64(BODY)
 * 2. signature = RSA-SHA256(canonicalRequest, privateKey)
 * 3. X-Signature = Base64(signature)
 * </pre>
 * <p>
 * TIMESTAMP 为秒级 Unix 时间戳字符串（X-Timestamp header 同值）。
 * 时间窗口：5 分钟（X-Timestamp 与服务器时间差 > 5 分钟 → 401）。
 * 纯 JDK 实现，零依赖。
 * <p>
 * 设计考量：签名原文与 Webhook 验签原文（t.body 拼接串）完全不同——单位（秒/毫秒）与
 * 原文结构均不同，严禁与 WaffoPancakeWebhookVerifier 共用拼装逻辑。
 */
public final class RequestSigner {

    private RequestSigner() {
    }

    /**
     * 生成请求签名（X-Signature header 值）。
     *
     * @param method           HTTP 方法（POST/GET 等大写）
     * @param path             请求路径（如 /v1/actions/checkout/create-session），不含 host/query
     * @param bodyBytes        请求体 UTF-8 字节（GET 请求传空字节数组）
     * @param timestampSeconds 秒级时间戳字符串（与 X-Timestamp 同值）
     * @param privateKey       RSA 私钥
     * @return Base64 编码签名字符串
     */
    public static String sign(String method, String path, byte[] bodyBytes, String timestampSeconds, PrivateKey privateKey) {
        try {
            String bodyHash = sha256Base64(bodyBytes);
            String canonicalRequest = method + "\n" + path + "\n" + timestampSeconds + "\n" + bodyHash;
            byte[] canonicalBytes = canonicalRequest.getBytes(StandardCharsets.UTF_8);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(canonicalBytes);
            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign Waffo Pancake request: " + e.getMessage(), e);
        }
    }

    /**
     * 计算请求体的 SHA-256 散列的 Base64 编码（非 hex，对齐 Pancake 文档 SHA256_BASE64(BODY)）。
     */
    public static String sha256Base64(byte[] bodyBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bodyBytes == null ? new byte[0] : bodyBytes);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 of request body", e);
        }
    }
}
