package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Codex 用量服务  * <p>
 * 通过 ChatGPT backend-api/wham/usage 端点查询 Codex 渠道的用量信息。
 */
@Slf4j
@Service
public class CodexUsageService {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** proxy-aware 客户端缓存 */
    private final Map<String, HttpClient> proxyClients = new ConcurrentHashMap<>();
    private final ReentrantLock proxyClientLock = new ReentrantLock();

    /** 清空 proxyClients 缓存，由 ProxyClientCacheService 委托调用 */
    public void resetProxyClientCache() {
        proxyClients.clear();
    }

    /**
     * Codex 用量查询结果
     */
    public static class CodexUsageResult {
        private final int statusCode;
        private final String body;
        private final String error;

        public CodexUsageResult(int statusCode, String body, String error) {
            this.statusCode = statusCode;
            this.body = body;
            this.error = error;
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
        public String getError() { return error; }
        public boolean isSuccess() { return statusCode >= 200 && statusCode < 300 && error == null; }
    }

    /**
     * 查询 Codex 渠道用量（无代理）      */
    public CodexUsageResult fetchUsage(String baseURL, String accessToken, String accountID) {
        return fetchUsageWithProxy(baseURL, accessToken, accountID, null);
    }

    /**
     * 查询 Codex 渠道用量（带代理）      *
     * @param baseURL     渠道 BaseURL
     * @param accessToken OAuth access_token
     * @param accountID   ChatGPT account_id
     * @param proxyURL    代理 URL，null 表示直连
     * @return 用量查询结果
     */
    public CodexUsageResult fetchUsageWithProxy(String baseURL, String accessToken, String accountID, String proxyURL) {
        String bu = baseURL == null ? "" : baseURL.trim();
        if (bu.endsWith("/")) {
            bu = bu.substring(0, bu.length() - 1);
        }
        if (bu.isEmpty()) {
            return new CodexUsageResult(0, null, "empty baseURL");
        }
        String at = accessToken == null ? "" : accessToken.trim();
        if (at.isEmpty()) {
            return new CodexUsageResult(0, null, "empty accessToken");
        }
        String aid = accountID == null ? "" : accountID.trim();
        if (aid.isEmpty()) {
            return new CodexUsageResult(0, null, "empty accountID");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(bu + "/backend-api/wham/usage"))
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + at)
                    .header("chatgpt-account-id", aid)
                    .header("Accept", "application/json")
                    .header("originator", "codex_cli_rs")
                    .GET()
                    .build();

            HttpClient client = resolveUsageHttpClient(proxyURL);
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new CodexUsageResult(resp.statusCode(), resp.body(), null);
        } catch (Exception e) {
            log.error("failed to fetch codex usage: {}", e.getMessage());
            return new CodexUsageResult(0, null, e.getMessage());
        }
    }

    /**
     * 解析用量查询用的 HttpClient（含代理）      */
    private HttpClient resolveUsageHttpClient(String proxyURL) {
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
                    log.warn("Codex Usage 不支持的代理协议, proxy={}, 回退为直连", key);
                    return httpClient;
                }
                return HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .proxy(ProxySelector.of(InetSocketAddress.createUnresolved(
                                proxyUri.getHost(),
                                proxyUri.getPort() == -1 ? 80 : proxyUri.getPort())))
                        .build();
            } catch (Exception e) {
                log.warn("Codex Usage 创建代理客户端失败, proxy={}, 回退为直连: {}", key, e.getMessage());
                return httpClient;
            } finally {
                proxyClientLock.unlock();
            }
        });
    }

    /**
     * 记录 Codex 使用量（运行时统计      * <p>
     * 当前阶段用量统计通过日志记录，后续可扩展为持久化。
     *
     * @param userId           用户 ID
     * @param promptTokens     输入 tokens
     * @param completionTokens 输出 tokens
     */
    public void recordUsage(int userId, int promptTokens, int completionTokens) {
        log.info("codex usage recorded: userId={}, prompt={}, completion={}",
                userId, promptTokens, completionTokens);
    }
}
