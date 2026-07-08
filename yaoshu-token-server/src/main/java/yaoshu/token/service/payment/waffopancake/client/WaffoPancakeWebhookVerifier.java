package yaoshu.token.service.payment.waffopancake.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeWebhookEvent;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Waffo Pancake Webhook 验签器（RFC §1.4 算法实现）。
 * <p>
 * 算法（权威定义：https://docs.waffo.ai/api-reference/webhooks#signature-verification）：
 * <pre>
 * 1. 解析 X-Waffo-Signature: t=<timestamp_ms>,v1=<Base64 signature>
 * 2. 重放防护: |t - 当前毫秒时间戳| ≤ 5 分钟
 * 3. 构造验签原文: signedPayload = t + "." + rawRequestBody
 * 4. 按 body.mode 选公钥（test/prod），用对应公钥 RSA-SHA256 验证 v1
 * </pre>
 * <p>
 * Java 端必要偏离：Go SDK 内嵌 test/prod 公钥，Java 从 options 表读
 *（WaffoPancakeWebhookPublicKeyTest / WaffoPancakeWebhookPublicKeyProd 两个 key）。
 * <p>
 * 验签策略（解决 mode 与公钥绑定的鸡生蛋问题）：先解析 body 拿 mode（验签后），但构造验签原文不需 mode。
 * 流程：① 解析 t/v1 → ② 重放校验 → ③ 试 test 与 prod 公钥验签 → ④ 验签成功者必须与 body.mode 一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaffoPancakeWebhookVerifier {

    /** 重放窗口（毫秒，5 分钟，对齐 Pancake 文档推荐） */
    private static final long REPLAY_WINDOW_MS = 5L * 60 * 1000;

    private final OptionService optionService;

    /**
     * 验签并解析事件。
     *
     * @param rawBody         原始请求体文本（必须 raw，禁止先 JSON 解析）
     * @param signatureHeader X-Waffo-Signature header（格式：t=...,v1=...）
     * @return 验签通过的事件对象
     * @throws WaffoPancakeWebhookException 验签失败的语义化原因
     */
    public WaffoPancakeWebhookEvent verify(String rawBody, String signatureHeader) {
        if (rawBody == null) {
            throw new WaffoPancakeWebhookException(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER,
                    "Webhook raw body is null");
        }
        // 步骤 1: 解析 t 与 v1
        long t;
        String v1;
        try {
            String[] kv = parseSignatureHeader(signatureHeader);
            t = Long.parseLong(kv[0]);
            v1 = kv[1];
        } catch (Exception e) {
            throw new WaffoPancakeWebhookException(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER,
                    "Malformed X-Waffo-Signature header: " + e.getMessage());
        }

        // 步骤 2: 重放防护（t 是毫秒级时间戳）
        long now = System.currentTimeMillis();
        if (Math.abs(now - t) > REPLAY_WINDOW_MS) {
            throw new WaffoPancakeWebhookException(WaffoPancakeWebhookException.Reason.REPLAY_WINDOW_EXCEEDED,
                    "Webhook timestamp out of replay window: t=" + t + " now=" + now);
        }

        // 步骤 3: 构造验签原文
        String signedPayload = t + "." + rawBody;
        byte[] signedBytes = signedPayload.getBytes(StandardCharsets.UTF_8);
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(v1);
        } catch (IllegalArgumentException e) {
            throw new WaffoPancakeWebhookException(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER,
                    "v1 signature is not valid Base64: " + e.getMessage());
        }

        // 步骤 4: 试 test 与 prod 公钥验签（公钥动态从 options 表读，响应管理员配置变更）
        String testPublicKeyPem = optionService.getValue("WaffoPancakeWebhookPublicKeyTest");
        String prodPublicKeyPem = optionService.getValue("WaffoPancakeWebhookPublicKeyProd");

        boolean testVerified = false;
        boolean prodVerified = false;
        if (testPublicKeyPem != null && !testPublicKeyPem.isBlank()) {
            testVerified = verifyRsaSha256(signedBytes, signatureBytes, testPublicKeyPem);
        }
        if (prodPublicKeyPem != null && !prodPublicKeyPem.isBlank()) {
            prodVerified = verifyRsaSha256(signedBytes, signatureBytes, prodPublicKeyPem);
        }
        if (!testVerified && !prodVerified) {
            throw new WaffoPancakeWebhookException(WaffoPancakeWebhookException.Reason.SIGNATURE_INVALID,
                    "Webhook signature verification failed (both test & prod public keys rejected)");
        }

        // 步骤 5: 解析 body 并校验 mode 与验签成功公钥一致
        JSONObject root;
        try {
            root = JSON.parseObject(rawBody);
        } catch (Exception e) {
            throw new WaffoPancakeWebhookException(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER,
                    "Webhook body is not valid JSON after signature verification: " + e.getMessage());
        }
        String mode = root.getString("mode");
        if ("test".equals(mode) && !testVerified) {
            throw new WaffoPancakeWebhookException(WaffoPancakeWebhookException.Reason.MODE_KEY_MISMATCH,
                    "Webhook mode=test but only prod key verified");
        }
        if ("prod".equals(mode) && !prodVerified) {
            throw new WaffoPancakeWebhookException(WaffoPancakeWebhookException.Reason.MODE_KEY_MISMATCH,
                    "Webhook mode=prod but only test key verified");
        }
        if (mode == null || (!"test".equals(mode) && !"prod".equals(mode))) {
            throw new WaffoPancakeWebhookException(WaffoPancakeWebhookException.Reason.MALFORMED_SIGNATURE_HEADER,
                    "Webhook body missing or invalid 'mode' field: " + mode);
        }

        return WaffoPancakeWebhookEvent.fromJson(root);
    }

    private static String[] parseSignatureHeader(String header) {
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("X-Waffo-Signature header is blank");
        }
        String t = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("t=")) {
                t = trimmed.substring(2).trim();
            } else if (trimmed.startsWith("v1=")) {
                v1 = trimmed.substring(3).trim();
            }
        }
        if (t == null || v1 == null) {
            throw new IllegalArgumentException("Missing t or v1 in X-Waffo-Signature header");
        }
        return new String[]{t, v1};
    }

    private static boolean verifyRsaSha256(byte[] signedBytes, byte[] signatureBytes, String publicKeyPem) {
        try {
            PublicKey publicKey = PemKeyLoader.loadPublicKey(publicKeyPem);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signedBytes);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            // 验签失败原因可能是公钥错误/格式错误/签名不匹配，统一返回 false
            return false;
        }
    }
}
