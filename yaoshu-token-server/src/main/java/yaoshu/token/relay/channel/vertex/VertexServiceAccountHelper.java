package yaoshu.token.relay.channel.vertex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import ai.yue.library.base.convert.Convert;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vertex AI Service Account 认证助手  * <p>
 * 通过 Service Account 的私钥创建 JWT，换取 OAuth2 access_token。
 * access_token 缓存 30 分钟（Go asynccache 35min refresh / 30min expire）。
 */
@Slf4j
public class VertexServiceAccountHelper {    private static final String TOKEN_URL = "https://www.googleapis.com/oauth2/v4/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final long TOKEN_TTL_SECONDS = 35 * 60; // 35 分钟

    /** access_token 缓存：cacheKey → [token, expireTimestamp] */
    private static final Map<String, long[]> TOKEN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> TOKEN_VALUE_CACHE = new ConcurrentHashMap<>();

    /**
     * Service Account 凭证      */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Credentials {
        @JsonProperty("project_id")
        private String projectId;
        @JsonProperty("private_key_id")
        private String privateKeyId;
        @JsonProperty("private_key")
        private String privateKey;
        @JsonProperty("client_email")
        private String clientEmail;
        @JsonProperty("client_id")
        private String clientId;
    }

    /**
     * 获取 access_token（带缓存）      */
    public static String getAccessToken(Credentials creds, String cacheKey, String proxy) throws Exception {
        // 检查缓存
        long[] cached = TOKEN_CACHE.get(cacheKey);
        if (cached != null && cached[0] > Instant.now().getEpochSecond()) {
            return TOKEN_VALUE_CACHE.get(cacheKey);
        }

        // 创建 JWT
        String signedJWT = createSignedJWT(creds.getClientEmail(), creds.getPrivateKey());
        // 换取 access_token
        String newToken = exchangeJwtForAccessToken(signedJWT, proxy);

        // 写入缓存
        TOKEN_VALUE_CACHE.put(cacheKey, newToken);
        TOKEN_CACHE.put(cacheKey, new long[]{Instant.now().getEpochSecond() + TOKEN_TTL_SECONDS, 0});

        return newToken;
    }

    /**
     * 直接获取 access_token（无缓存）      */
    public static String acquireAccessToken(Credentials creds, String proxy) throws Exception {
        String signedJWT = createSignedJWT(creds.getClientEmail(), creds.getPrivateKey());
        return exchangeJwtForAccessToken(signedJWT, proxy);
    }

    /**
     * 创建签名 JWT      */
    static String createSignedJWT(String email, String privateKeyPEM) throws Exception {
        // 清理 PEM 格式标记和换行
        String cleanedKey = privateKeyPEM
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("\\n", "")
                .trim();

        // 解析 PKCS8 私钥
        byte[] keyBytes = Base64.getDecoder().decode(cleanedKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

        // JWT Header
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";

        // JWT Claims
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", email);
        claims.put("scope", SCOPE);
        claims.put("aud", TOKEN_URL);
        claims.put("exp", now + TOKEN_TTL_SECONDS);
        claims.put("iat", now);

        String claimsJson = Convert.toJSONString(claims);

        // Base64url 编码
        String encodedHeader = base64UrlEncode(header.getBytes(StandardCharsets.UTF_8));
        String encodedClaims = base64UrlEncode(claimsJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedClaims;

        // RS256 签名
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signature.sign();

        return signingInput + "." + base64UrlEncode(signatureBytes);
    }

    /**
     * 用 JWT 换取 access_token      */
    @SuppressWarnings("unchecked")
    static String exchangeJwtForAccessToken(String signedJWT, String proxy) throws Exception {
        String formData = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(signedJWT, StandardCharsets.UTF_8);

        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        // proxy 配置由外层处理（当前默认不走代理）
        HttpClient client = clientBuilder.build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Map<String, Object> result = Convert.toJSONObject(response.body());
        String accessToken = (String) result.get("access_token");
        if (accessToken == null) {
            throw new RuntimeException("failed to get access token: " + result);
        }
        return accessToken;
    }

    /** Base64url 编码（无填充） */
    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
