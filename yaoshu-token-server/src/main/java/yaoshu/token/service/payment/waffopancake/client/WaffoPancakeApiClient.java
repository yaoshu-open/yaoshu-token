package yaoshu.token.service.payment.waffopancake.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;

/**
 * Waffo Pancake REST API 客户端（HTTP + 签名引擎）。
 * <p>
 * 持有 java.net.http.HttpClient 单例（全局架构决策：强制 JDK HttpClient，禁 OkHttp/Apache/Forest），
 * 装配 X-Merchant-Id / X-Timestamp / X-Signature 认证 header（RFC §1.2），
 * 统一映射 Pancake HTTP 错误响应为 {@link WaffoPancakeApiException}。
 * <p>
 * Pancake API 基址：{@value #DEFAULT_BASE_URL}（RFC §1.5 WP-02 已确认）。
 */
public class WaffoPancakeApiClient {

    /** Pancake API 权威基址（RFC §1.5 WP-02 已确认，禁止 api.waffo.com） */
    public static final String DEFAULT_BASE_URL = "https://api.waffo.ai";

    private final HttpClient httpClient;
    private final String baseUrl;

    /**
     * 默认构造：使用权威基址 + JDK HttpClient 单例（10s 连接超时）。
     */
    public WaffoPancakeApiClient() {
        this(DEFAULT_BASE_URL);
    }

    public WaffoPancakeApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 执行签名请求（API Key 模式）。
     *
     * @param method     HTTP 方法（POST/GET 大写）
     * @param path       请求路径（如 /v1/actions/auth/issue-session-token）
     * @param bodyBytes  请求体字节（GET 传 null 或空字节数组）
     * @param merchantId 商户 ID（MER_xxx）
     * @param privateKey RSA 私钥
     * @return 响应体字符串（UTF-8 解码）
     * @throws WaffoPancakeApiException HTTP 非 2xx 或网络错误
     */
    public String execute(String method, String path, byte[] bodyBytes, String merchantId, PrivateKey privateKey) {
        byte[] body = bodyBytes == null ? new byte[0] : bodyBytes;
        String timestampSeconds = String.valueOf(Instant.now().getEpochSecond());
        String signature = RequestSigner.sign(method, path, body, timestampSeconds, privateKey);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Merchant-Id", merchantId)
                .header("X-Timestamp", timestampSeconds)
                .header("X-Signature", signature);

        HttpRequest.BodyPublisher bodyPublisher = body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        builder.method(method, bodyPublisher);

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int statusCode = response.statusCode();
            byte[] respBytes = response.body();
            if (statusCode < 200 || statusCode >= 300) {
                String respText = respBytes == null ? "" : new String(respBytes, StandardCharsets.UTF_8);
                throw new WaffoPancakeApiException(
                        "Waffo Pancake API error: HTTP " + statusCode + " path=" + path + " body=" + truncate(respText, 500),
                        statusCode);
            }
            return respBytes == null ? "" : new String(respBytes, StandardCharsets.UTF_8);
        } catch (WaffoPancakeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new WaffoPancakeApiException("Failed to call Waffo Pancake API path=" + path + ": " + e.getMessage(), e);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "...(truncated)" : s;
    }
}
