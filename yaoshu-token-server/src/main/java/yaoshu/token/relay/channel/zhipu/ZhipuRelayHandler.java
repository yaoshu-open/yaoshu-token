package yaoshu.token.relay.channel.zhipu;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.relay.channel.zhipu.ZhipuDTOPlaceholder.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智谱 ChatGLM 中转处理器  * <p>
 * 包含 JWT Token 生成（HS256）、请求转换、流式/非流式响应处理。
 */
@Slf4j
public class ZhipuRelayHandler {    private static final long EXP_SECONDS = 24 * 3600;

    /** Token 缓存：apiKey → [token, expiryEpochMillis] */
    private static final Map<String, Object[]> TOKEN_CACHE = new ConcurrentHashMap<>();

    /**
     * 生成智谱 JWT Token      * <p>
     * 智谱 API Key 格式为 id.secret，使用 HS256 签名。
     */
    public static String getZhipuToken(String apiKey) {
        if (apiKey == null) return "";

        // 检查缓存
        Object[] cached = TOKEN_CACHE.get(apiKey);
        if (cached != null && System.currentTimeMillis() < (long) cached[1]) {
            return (String) cached[0];
        }

        String[] split = apiKey.split("\\.");
        if (split.length != 2) {
            log.warn("invalid zhipu key: {}", apiKey);
            return "";
        }

        String id = split[0];
        String secret = split[1];

        long expMillis = System.currentTimeMillis() + EXP_SECONDS * 1000;
        long timestamp = System.currentTimeMillis();

        // JWT Header
        String header = "{\"alg\":\"HS256\",\"sign_type\":\"SIGN\"}";

        // JWT Payload
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api_key", id);
        payload.put("exp", expMillis);
        payload.put("timestamp", timestamp);

        try {
            String payloadJson = Convert.toJSONString(payload);
            String encodedHeader = base64UrlEncode(header.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));

            String token = signingInput + "." + base64UrlEncode(signature);

            TOKEN_CACHE.put(apiKey, new Object[]{token, expMillis});
            return token;
        } catch (Exception e) {
            log.error("failed to generate zhipu token", e);
            return "";
        }
    }

    /**
     * OpenAI 请求 → 智谱请求      * <p>
     * system 消息后追加 "Okay" 用户消息（智谱特殊要求）。
     */
    @SuppressWarnings("unchecked")
    public static ZhipuRequest requestOpenAI2Zhipu(GeneralOpenAIRequest request) {
        List<ZhipuMessage> messages = new ArrayList<>();
        List<Object> rawMessages = (List<Object>) (Object) request.getMessages();
        if (rawMessages != null) {
            for (Object msgObj : rawMessages) {
                if (msgObj instanceof Map) {
                    Map<String, Object> msg = (Map<String, Object>) msgObj;
                    String role = (String) msg.get("role");
                    Object content = msg.get("content");
                    String contentStr = contentToString(content);

                    ZhipuMessage zhipuMsg = new ZhipuMessage();
                    zhipuMsg.setRole(role);
                    zhipuMsg.setContent(contentStr);
                    messages.add(zhipuMsg);

                    // system 消息后追加 Okay
                    if ("system".equals(role)) {
                        ZhipuMessage okMsg = new ZhipuMessage();
                        okMsg.setRole("user");
                        okMsg.setContent("Okay");
                        messages.add(okMsg);
                    }
                }
            }
        }

        ZhipuRequest zhipuReq = new ZhipuRequest();
        zhipuReq.setPrompt(messages);
        zhipuReq.setTemperature(request.getTemperature());
        zhipuReq.setTopP(request.getTopP() != null ? request.getTopP() : 0.0);
        zhipuReq.setIncremental(false);
        return zhipuReq;
    }

    /** 将 content（可能是 String 或 List）转为纯文本 */
    @SuppressWarnings("unchecked")
    private static String contentToString(Object content) {
        if (content == null) return "";
        if (content instanceof String s) return s;
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    if ("text".equals(m.get("type"))) {
                        sb.append(m.get("text"));
                    }
                }
            }
            return sb.toString();
        }
        return content.toString();
    }

    /** Base64url 编码（无填充） */
    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
