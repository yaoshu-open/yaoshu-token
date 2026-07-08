package yaoshu.token.service;

import com.fasterxml.jackson.databind.JsonNode;
import ai.yue.library.base.convert.Convert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * 上游同步 HTTP 服务。
 * <p>
 * 为模型同步与比率同步复用：重试、ETag、响应体缓存、JSON 解析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpstreamSyncHttpService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String GITHUB_IO_HOST_SUFFIX = "github.io";

    private static final Map<String, String> ETAG_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> BODY_CACHE = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public JsonNode fetchJson(String url, int timeoutSeconds, int maxMegabytes) throws IOException, InterruptedException {
        return fetchJson(url, timeoutSeconds, maxMegabytes, null);
    }

    /** 支持自定义 HTTP 头部的拉取重载（如 OpenRouter Authorization Bearer 注入） */
    public JsonNode fetchJson(String url, int timeoutSeconds, int maxMegabytes,
                              Map<String, String> headers) throws IOException, InterruptedException {
        byte[] body = fetchBytes(url, timeoutSeconds, maxMegabytes, headers);
        return objectMapper.readTree(body);
    }

    public byte[] fetchBytes(String url, int timeoutSeconds, int maxMegabytes) throws IOException, InterruptedException {
        return fetchBytes(url, timeoutSeconds, maxMegabytes, null);
    }

    public byte[] fetchBytes(String url, int timeoutSeconds, int maxMegabytes,
                             Map<String, String> headers) throws IOException, InterruptedException {
        int attempts = Math.max(readIntEnv("SYNC_HTTP_RETRY", 3), 1);
        int timeout = Math.max(timeoutSeconds, 1);
        int maxBytes = Math.max(maxMegabytes, 1) * 1024 * 1024;
        Exception lastError = null;

        // github.io 主机 IPv4 优先：解析出 IPv4 字面地址替换 URL 中的 host，原 host 写入 Host header
        // 规避中国大陆 IPv6 路由不稳定问题
        String[] urlAndHost = rewriteGithubIoUrlToIPv4(url);
        String requestUrl = urlAndHost[0];
        String originalHost = urlAndHost[1];
        // 副本化 headers 以避免修改入参，注入 Host header
        Map<String, String> effectiveHeaders = headers == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(headers);
        if (originalHost != null) {
            effectiveHeaders.put("Host", originalHost);
        }

        for (int attempt = 0; attempt < attempts; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(requestUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(timeout));
            String etag = ETAG_CACHE.get(url);
            if (etag != null && !etag.isBlank()) {
                builder.header("If-None-Match", etag);
            }
            // 自定义头部注入（如 OpenRouter Authorization Bearer / github.io Host 头）
            if (!effectiveHeaders.isEmpty()) {
                for (Map.Entry<String, String> entry : effectiveHeaders.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        builder.header(entry.getKey(), entry.getValue());
                    }
                }
            }
            HttpRequest request = builder.build();
            try {
                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 304) {
                    byte[] cached = BODY_CACHE.get(url);
                    if (cached != null) {
                        return cached;
                    }
                    throw new IOException(I18nUtils.get("upstream.not_modified_not_cached"));
                }
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                byte[] body = response.body();
                if (body.length > maxBytes) {
                    throw new IOException(I18nUtils.get("upstream.response_too_large", url));
                }
                String newEtag = response.headers().firstValue("ETag").orElse(null);
                if (newEtag != null && !newEtag.isBlank()) {
                    ETAG_CACHE.put(url, newEtag);
                }
                BODY_CACHE.put(url, body);
                return body;
            } catch (IOException | InterruptedException e) {
                lastError = e;
                if (e instanceof InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
                try {
                    Thread.sleep((long) (200L * Math.pow(2, attempt)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }

        if (lastError instanceof IOException ioException) {
            throw ioException;
        }
        if (lastError instanceof InterruptedException interruptedException) {
            throw interruptedException;
        }
        throw new IOException(I18nUtils.get("upstream.request_failed", url));
    }

    public JsonNode extractDataArrayOrRoot(JsonNode root) {
        if (root == null || root.isNull()) {
            return objectMapper.createArrayNode();
        }
        JsonNode dataNode = root.get("data");
        if (dataNode != null && dataNode.isArray()) {
            return dataNode;
        }
        if (root.isArray()) {
            return root;
        }
        return objectMapper.createArrayNode();
    }

    private int readIntEnv(String key, int defaultValue) {
        String raw = System.getenv(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer env {}={}, fallback {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 将 github.io 主机的 URL 重写为 IPv4 字面地址。      * <p>
     * 仅对 host 后缀为 github.io 的 URL 生效：解析全部 InetAddress，筛选首个 IPv4 地址，
     * 用 IP 字面地址替换 host 部分，原 host 通过返回数组第二项由调用方注入 Host header。
     * 解析失败 / 无 IPv4 / 非 github.io 主机时返回 [原URL, null]。
     */
    private String[] rewriteGithubIoUrlToIPv4(String url) {
        if (url == null || url.isEmpty()) return new String[]{url, null};
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !host.toLowerCase().endsWith(GITHUB_IO_HOST_SUFFIX)) {
                return new String[]{url, null};
            }
            java.net.InetAddress[] all = java.net.InetAddress.getAllByName(host);
            String ipv4 = null;
            for (java.net.InetAddress addr : all) {
                if (addr instanceof java.net.Inet4Address) {
                    ipv4 = addr.getHostAddress();
                    break;
                }
            }
            if (ipv4 == null) return new String[]{url, null};
            int port = uri.getPort();
            String replacement = port > 0 ? ipv4 + ":" + port : ipv4;
            String rewritten = url.replace(host, replacement);
            return new String[]{rewritten, host + (port > 0 ? ":" + port : "")};
        } catch (Exception e) {
            log.debug("rewriteGithubIoUrlToIPv4 失败 url={}: {}", url, e.getMessage());
            return new String[]{url, null};
        }
    }
}
