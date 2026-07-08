package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Codex OAuth 服务  * <p>
 * 处理 OpenAI Codex OAuth 授权流程（PKCE 授权码交换、令牌获取与刷新）。
 */
@Slf4j
@Service
public class CodexOAuthService {

    // Codex OAuth 固定参数
    private static final String CODEX_OAUTH_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
    private static final String CODEX_OAUTH_AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize";
    private static final String CODEX_OAUTH_TOKEN_URL = "https://auth.openai.com/oauth/token";
    private static final String CODEX_OAUTH_REDIRECT_URI = "http://localhost:1455/auth/callback";
    private static final String CODEX_OAUTH_SCOPE = "openid profile email offline_access";
    private static final String CODEX_JWT_CLAIM_PATH = "https://api.openai.com/auth";
    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(20);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER_NO_PADDING = Base64.getUrlEncoder().withoutPadding();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** proxy-aware 客户端缓存，key = proxy URL*/
    private final Map<String, HttpClient> proxyClients = new ConcurrentHashMap<>();
    private final ReentrantLock proxyClientLock = new ReentrantLock();

    /** 清空 proxyClients 缓存，由 ProxyClientCacheService 委托调用 */
    public void resetProxyClientCache() {
        proxyClients.clear();
    }

    /**
     * OAuth 令牌结果      */
    public static class CodexOAuthTokenResult {
        private final String accessToken;
        private final String refreshToken;
        private final LocalDateTime expiresAt;

        public CodexOAuthTokenResult(String accessToken, String refreshToken, LocalDateTime expiresAt) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }

    /**
     * OAuth 授权流      */
    public static class CodexOAuthAuthorizationFlow {
        private final String state;
        private final String verifier;
        private final String challenge;
        private final String authorizeURL;

        public CodexOAuthAuthorizationFlow(String state, String verifier, String challenge, String authorizeURL) {
            this.state = state;
            this.verifier = verifier;
            this.challenge = challenge;
            this.authorizeURL = authorizeURL;
        }

        public String getState() { return state; }
        public String getVerifier() { return verifier; }
        public String getChallenge() { return challenge; }
        public String getAuthorizeURL() { return authorizeURL; }
    }

    /**
     * 使用授权码交换令牌（PKCE）      *
     * @param code     授权码
     * @param verifier PKCE code_verifier
     * @return 令牌结果，失败抛异常
     */
    public CodexOAuthTokenResult exchangeCodeForToken(String code, String verifier) {
        return exchangeCodeForTokenWithProxy(code, verifier, null);
    }

    /**
     * 使用授权码交换令牌（带代理）      */
    public CodexOAuthTokenResult exchangeCodeForTokenWithProxy(String code, String verifier, String proxyURL) {
        String c = code == null ? "" : code.trim();
        String v = verifier == null ? "" : verifier.trim();
        if (c.isEmpty()) {
            throw new IllegalArgumentException("empty authorization code");
        }
        if (v.isEmpty()) {
            throw new IllegalArgumentException("empty code_verifier");
        }

        // 构建 form 表单
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", CODEX_OAUTH_CLIENT_ID);
        form.put("code", c);
        form.put("code_verifier", v);
        form.put("redirect_uri", CODEX_OAUTH_REDIRECT_URI);

        return postTokenEndpoint(resolveOAuthHttpClient(proxyURL), form, "codex oauth code exchange failed");
    }

    /**
     * 使用 refresh_token 刷新令牌      *
     * @param refreshToken 刷新令牌
     * @return 令牌结果，失败抛异常
     */
    public CodexOAuthTokenResult refreshToken(String refreshToken) {
        return refreshTokenWithProxy(refreshToken, null);
    }

    /**
     * 使用 refresh_token 刷新令牌（带代理）      */
    public CodexOAuthTokenResult refreshTokenWithProxy(String refreshToken, String proxyURL) {
        String rt = refreshToken == null ? "" : refreshToken.trim();
        if (rt.isEmpty()) {
            throw new IllegalArgumentException("empty refresh_token");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", rt);
        form.put("client_id", CODEX_OAUTH_CLIENT_ID);

        return postTokenEndpoint(resolveOAuthHttpClient(proxyURL), form, "codex oauth refresh failed");
    }

    /**
     * 创建 OAuth 授权流（PKCE）      */
    public CodexOAuthAuthorizationFlow createAuthorizationFlow() {
        String state = createStateHex(16);
        String[] pkce = generatePKCEPair();
        String verifier = pkce[0];
        String challenge = pkce[1];
        String authorizeURL = buildAuthorizeURL(state, challenge);
        return new CodexOAuthAuthorizationFlow(state, verifier, challenge, authorizeURL);
    }

    /**
     * 构建授权 URL（兼容旧接口签名）      */
    public String buildAuthURL(String clientId, String redirectUri, String state) {
        // 此方法保留兼容，实际使用 createAuthorizationFlow().getAuthorizeURL()
        return buildAuthorizeURL(state, generatePKCEPair()[1]);
    }

    /**
     * 从 JWT access_token 中提取 ChatGPT account_id      */
    public static String extractAccountIDFromJWT(String token) {
        Map<String, Object> claims = decodeJWTClaims(token);
        if (claims == null) return null;
        Object obj = claims.get(CODEX_JWT_CLAIM_PATH);
        if (!(obj instanceof Map)) return null;
        Object accountId = ((Map<?, ?>) obj).get("chatgpt_account_id");
        if (!(accountId instanceof String s)) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 从 JWT 中提取 email      */
    public static String extractEmailFromJWT(String token) {
        Map<String, Object> claims = decodeJWTClaims(token);
        if (claims == null) return null;
        Object v = claims.get("email");
        if (!(v instanceof String s)) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    // ======================== 内部方法 ========================

    /**
     * POST 到 token 端点并解析响应      */
    private CodexOAuthTokenResult postTokenEndpoint(HttpClient client, Map<String, String> form, String errorMsg) {
        String formBody = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CODEX_OAUTH_TOKEN_URL))
                .timeout(DEFAULT_HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new RuntimeException(errorMsg + ": status=" + resp.statusCode());
            }

            // 使用 Convert 解析 JSON 响应
            Map<String, Object> payload = Convert.toJSONObject(resp.body());
            String accessToken = getTrimmed(payload, "access_token");
            String refreshToken = getTrimmed(payload, "refresh_token");
            Object expiresInObj = payload.get("expires_in");
            int expiresIn = expiresInObj instanceof Number n ? n.intValue() : 0;

            if (accessToken.isEmpty() || refreshToken.isEmpty() || expiresIn <= 0) {
                throw new RuntimeException("codex oauth token response missing fields");
            }

            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
            return new CodexOAuthTokenResult(accessToken, refreshToken, expiresAt);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(errorMsg + ": " + e.getMessage(), e);
        }
    }

    /**
     * 解析 OAuth 用的 HttpClient（含代理）      * <p>
     * 仅支持 http/https 代理 URL；无代理时返回默认 httpClient。
     */
    private HttpClient resolveOAuthHttpClient(String proxyURL) {
        String trimmed = proxyURL == null ? null : proxyURL.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return httpClient;
        }
        return proxyClients.computeIfAbsent(trimmed, key -> {
            proxyClientLock.lock();
            try {
                URI proxyUri = URI.create(key);
                String scheme = proxyUri.getScheme();
                if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                    log.warn("Codex OAuth 不支持的代理协议, proxy={}, 回退为直连", key);
                    return httpClient;
                }
                return HttpClient.newBuilder()
                        .connectTimeout(DEFAULT_HTTP_TIMEOUT)
                        .proxy(ProxySelector.of(InetSocketAddress.createUnresolved(
                                proxyUri.getHost(),
                                proxyUri.getPort() == -1 ? 80 : proxyUri.getPort())))
                        .build();
            } catch (Exception e) {
                log.warn("Codex OAuth 创建代理客户端失败, proxy={}, 回退为直连: {}", key, e.getMessage());
                return httpClient;
            } finally {
                proxyClientLock.unlock();
            }
        });
    }

    /**
     * 构建 Codex 授权 URL      */
    private String buildAuthorizeURL(String state, String challenge) {
        StringBuilder sb = new StringBuilder(CODEX_OAUTH_AUTHORIZE_URL);
        sb.append("?response_type=code");
        sb.append("&client_id=").append(URLEncoder.encode(CODEX_OAUTH_CLIENT_ID, StandardCharsets.UTF_8));
        sb.append("&redirect_uri=").append(URLEncoder.encode(CODEX_OAUTH_REDIRECT_URI, StandardCharsets.UTF_8));
        sb.append("&scope=").append(URLEncoder.encode(CODEX_OAUTH_SCOPE, StandardCharsets.UTF_8));
        sb.append("&code_challenge=").append(challenge);
        sb.append("&code_challenge_method=S256");
        sb.append("&state=").append(state);
        sb.append("&id_token_add_organizations=true");
        sb.append("&codex_cli_simplified_flow=true");
        sb.append("&originator=codex_cli_rs");
        return sb.toString();
    }

    /**
     * 生成 PKCE 密钥对      *
     * @return [verifier, challenge]
     */
    private static String[] generatePKCEPair() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String verifier = URL_ENCODER_NO_PADDING.encodeToString(bytes);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String challenge = URL_ENCODER_NO_PADDING.encodeToString(hash);
            return new String[]{verifier, challenge};
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PKCE challenge", e);
        }
    }

    /**
     * 生成随机 state（十六进制）      */
    private static String createStateHex(int nBytes) {
        if (nBytes <= 0) {
            throw new IllegalArgumentException("invalid state bytes length");
        }
        byte[] bytes = new byte[nBytes];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 解码 JWT claims      */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> decodeJWTClaims(String token) {
        if (token == null || token.isEmpty()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            return Convert.toJSONObject(payloadJson);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getTrimmed(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return "";
        return v.toString().trim();
    }
}
