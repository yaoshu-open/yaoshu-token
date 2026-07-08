package yaoshu.token.relay.channel;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.GeneralSettingConfig;
import yaoshu.token.relay.common.OutboundBodyHelper;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.helper.RelayCommonHelper;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Relay API 请求执行器  * <p>
 * 提供上游 HTTP 请求构造、Header 透传与覆写、SSE Ping 保活、
 * WebSocket 拨号、Task API 请求等核心转发能力。
 */
@Slf4j
public final class ApiRequestExecutor {

    private ApiRequestExecutor() {
    }

    // ======================== 常量 ========================

    private static final String CLIENT_HEADER_PLACEHOLDER_PREFIX = "{client_header:";
    static final String HEADER_PASSTHROUGH_ALL_KEY = "*";
    private static final String HEADER_PASSTHROUGH_REGEX_PREFIX = "re:";
    private static final String HEADER_PASSTHROUGH_REGEX_PREFIX_V2 = "regex:";

    // RFC 7230 hop-by-hop + 不应透传的头
    private static final Set<String> PASSTHROUGH_SKIP_HEADER_NAMES = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade",
            "cookie", "host", "content-length", "accept-encoding",
            "authorization", "x-api-key", "x-goog-api-key",
            "sec-websocket-key", "sec-websocket-version", "sec-websocket-extensions"
    );

    /** Header Passthrough 正则缓存 */
    private static final Map<String, Pattern> HEADER_PASSTHROUGH_REGEX_CACHE = new ConcurrentHashMap<>();

    // ======================== Content-Length ========================

    /**
     * 当上游 body 被 BodyStorage 包装（type-erased io.Reader）时，
     * 设置 Content-Length 避免强制分块传输      * <p>
     * Java HttpClient 中 Content-Length 为 restricted header，不可手动设置。
     * 改为在 doApiRequest/doFormRequest 等方法中将 body 转为 byte[] 再用
     * {@link java.net.http.HttpRequest.BodyPublishers#ofByteArray(byte[])} 发布——
     * JDK 会自动设置 Content-Length。
     */
    public static void applyUpstreamContentLength(HttpRequest.Builder builder, RelayInfo info) {
        // Java HttpClient 自动管理 Content-Length（ofByteArray publisher）
        // 不再手动 setHeader，避免 restricted header 异常
    }

    // ======================== 请求头设置 ========================

    /**
     * 设置基础 API 请求头      */
    public static void setupApiRequestHeader(
            Map<String, String> targetHeaders,
            Map<String, String> sourceHeaders,
            int relayMode,
            boolean isStream) {

        if (relayMode == RelayModeEnum.AUDIO_TRANSCRIPTION
                || relayMode == RelayModeEnum.AUDIO_TRANSLATION) {
            // multipart/form-data — 由调用方自行处理
            return;
        }

        if (relayMode == RelayModeEnum.REALTIME) {
            // WebSocket — 由 DoWssRequest 处理
            return;
        }

        String contentType = sourceHeaders != null ? sourceHeaders.get("Content-Type") : null;
        if (contentType != null) {
            targetHeaders.put("Content-Type", contentType);
        } else {
            // 兜底：OpenAI 兼容 API 默认 Content-Type
            targetHeaders.put("Content-Type", "application/json");
        }

        String accept = sourceHeaders != null ? sourceHeaders.get("Accept") : null;
        if (accept != null) {
            targetHeaders.put("Accept", accept);
        } else if (isStream) {
            targetHeaders.put("Accept", "text/event-stream");
        }
    }

    // ======================== Header 透传与覆写 ========================

    /**
     * 判断 key 是否为 Header Passthrough 规则键      */
    static boolean isHeaderPassthroughRuleKey(String key) {
        if (key == null) return false;
        key = key.trim().toLowerCase();
        if (key.isEmpty()) return false;
        if (HEADER_PASSTHROUGH_ALL_KEY.equals(key)) return true;
        return key.startsWith(HEADER_PASSTHROUGH_REGEX_PREFIX) || key.startsWith(HEADER_PASSTHROUGH_REGEX_PREFIX_V2);
    }

    /**
     * 判断响应头是否应跳过透传（不安全的头）      */
    static boolean shouldSkipPassthroughHeader(String name) {
        if (name == null) return true;
        name = name.trim();
        if (name.isEmpty()) return true;
        return PASSTHROUGH_SKIP_HEADER_NAMES.contains(name.toLowerCase());
    }

    /**
     * 编译/缓存 Header Passthrough 正则      */
    private static Pattern getHeaderPassthroughRegex(String pattern) {
        pattern = pattern.trim();
        if (pattern.isEmpty()) return null;
        return HEADER_PASSTHROUGH_REGEX_CACHE.computeIfAbsent(pattern, p -> {
            try {
                return Pattern.compile(p);
            } catch (Exception e) {
                log.warn("Invalid header passthrough regex: {}", p, e);
                return null;
            }
        });
    }

    /**
     * 解析 Header Override 占位符      * <p>
     * 支持的占位符：{api_key}、{client_header:name}
     *
     * @return null 表示不包含此 header，非 null 值表示替换后的 header 值
     */
    private static String applyHeaderOverridePlaceholders(String template, Map<String, String> clientHeaders, String apiKey) {
        String trimmed = template.trim();
        if (trimmed.startsWith(CLIENT_HEADER_PLACEHOLDER_PREFIX)) {
            String afterPrefix = trimmed.substring(CLIENT_HEADER_PLACEHOLDER_PREFIX.length());
            int end = afterPrefix.indexOf('}');
            if (end < 0 || end != afterPrefix.length() - 1) {
                log.warn("Invalid client_header placeholder: {}", template);
                return null;
            }
            String name = afterPrefix.substring(0, end).trim();
            if (name.isEmpty()) {
                log.warn("Empty client_header placeholder name: {}", template);
                return null;
            }
            if (clientHeaders == null) return null;
            String value = clientHeaders.get(name);
            if (value == null || value.trim().isEmpty()) return null;
            // 不内插 {api_key} 到客户端内容中
            return value;
        }

        if (template.contains("{api_key}")) {
            template = template.replace("{api_key}", apiKey != null ? apiKey : "");
        }
        if (template.trim().isEmpty()) return null;
        return template;
    }

    /**
     * 解析 Header Override 映射      * <p>
     * 处理顺序：先应用 pass-through 规则，再应用显式覆写（显式覆写优先级最高）
     */
    public static Map<String, String> resolveHeaderOverride(
            RelayInfo info,
            Map<String, Object> headerOverrideSource,
            Map<String, String> clientHeaders,
            String apiKey,
            boolean isChannelTest) {

        Map<String, String> result = new LinkedHashMap<>();
        if (info == null || headerOverrideSource == null || headerOverrideSource.isEmpty()) {
            return result;
        }

        boolean passAll = false;
        List<Pattern> passthroughRegex = new ArrayList<>();

        if (!isChannelTest) {
            for (String k : headerOverrideSource.keySet()) {
                String key = k.trim().toLowerCase();
                if (key.isEmpty()) continue;

                if (HEADER_PASSTHROUGH_ALL_KEY.equals(key)) {
                    passAll = true;
                    continue;
                }

                String pattern;
                if (key.startsWith(HEADER_PASSTHROUGH_REGEX_PREFIX)) {
                    pattern = key.substring(HEADER_PASSTHROUGH_REGEX_PREFIX.length()).trim();
                } else if (key.startsWith(HEADER_PASSTHROUGH_REGEX_PREFIX_V2)) {
                    pattern = key.substring(HEADER_PASSTHROUGH_REGEX_PREFIX_V2.length()).trim();
                } else {
                    continue;
                }

                if (pattern.isEmpty()) {
                    log.warn("Empty header passthrough regex: {}", k);
                    continue;
                }
                Pattern compiled = getHeaderPassthroughRegex(pattern);
                if (compiled != null) {
                    passthroughRegex.add(compiled);
                }
            }
        }

        // 第一步：应用 pass-through 规则
        if ((passAll || !passthroughRegex.isEmpty()) && clientHeaders != null) {
            for (Map.Entry<String, String> entry : clientHeaders.entrySet()) {
                String name = entry.getKey();
                if (shouldSkipPassthroughHeader(name)) continue;

                if (!passAll) {
                    boolean matched = false;
                    for (Pattern re : passthroughRegex) {
                        if (re.matcher(name).matches()) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) continue;
                }

                String value = entry.getValue();
                if (value != null && !value.trim().isEmpty()) {
                    result.put(name.toLowerCase().trim(), value.trim());
                }
            }
        }

        // 第二步：应用显式覆写（覆盖 pass-through 结果）
        for (Map.Entry<String, Object> entry : headerOverrideSource.entrySet()) {
            String k = entry.getKey();
            if (isHeaderPassthroughRuleKey(k)) continue;

            String key = k.trim().toLowerCase();
            if (key.isEmpty()) continue;

            String strValue;
            if (entry.getValue() instanceof String) {
                strValue = (String) entry.getValue();
            } else {
                strValue = String.valueOf(entry.getValue());
            }

            // 测试渠道跳过 client_header 占位符
            if (isChannelTest && strValue.trim().startsWith(CLIENT_HEADER_PLACEHOLDER_PREFIX)) {
                continue;
            }

            String value = applyHeaderOverridePlaceholders(strValue, clientHeaders, apiKey);
            if (value != null) {
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * 将 resolved header 应用到 HttpRequest.Builder      */
    public static void applyHeaderOverrideToRequest(HttpRequest.Builder builder, Map<String, String> headerOverride) {
        if (headerOverride == null) return;
        for (Map.Entry<String, String> entry : headerOverride.entrySet()) {
            builder.setHeader(entry.getKey(), entry.getValue());
        }
    }

    // ======================== 请求执行 ========================

    /**
     * 获取上游 HTTP 客户端（含代理）      */
    private static HttpClient getUpstreamHttpClient(String proxy) throws Exception {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL);

        if (proxy != null && !proxy.isEmpty()) {
            String proxyUrl = proxy.trim();
            // 解析 proxy URL：支持 http://host:port 格式
            if (proxyUrl.startsWith("http://") || proxyUrl.startsWith("https://")) {
                URI proxyUri = URI.create(proxyUrl);
                clientBuilder.proxy(ProxySelector.of(
                        new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())));
            } else {
                // 纯 host:port 格式
                String[] parts = proxyUrl.split(":");
                if (parts.length == 2) {
                    clientBuilder.proxy(ProxySelector.of(
                            new InetSocketAddress(parts[0], Integer.parseInt(parts[1]))));
                }
            }
        }

        return clientBuilder.build();
    }

    /**
     * 执行 API 请求      *
     * @param adaptor     渠道适配器
     * @param info        Relay 上下文
     * @param requestBody 上游请求体流
     * @param clientHeaders 客户端请求头（用于 header override 占位符解析）
     * @param method      HTTP 方法
     * @return 上游 HTTP 响应
     */
    public static HttpResponse<InputStream> doApiRequest(
            IAdaptor adaptor,
            RelayInfo info,
            InputStream requestBody,
            Map<String, String> clientHeaders,
            String method) throws Exception {

        String fullRequestURL = adaptor.getRequestURL(info);
        if (fullRequestURL == null || fullRequestURL.isEmpty()) {
            throw new IOException("get request url failed: empty url");
        }
        log.debug("fullRequestURL: {}", fullRequestURL);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullRequestURL));

        // ofByteArray 让 JDK 自动设置 Content-Length（DeepSeek/OpenAI 等需要）
        byte[] bodyBytes = requestBody != null ? requestBody.readAllBytes() : null;
        switch (method.toUpperCase()) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(bodyBytes == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
            case "PUT" -> builder.PUT(bodyBytes == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
            default -> builder.method(method.toUpperCase(), bodyBytes == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
        }

        applyUpstreamContentLength(builder, info);

        // 设置请求头
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        adaptor.setupRequestHeader(info);  // 渠道自定义头

        // 基础头设置
        setupApiRequestHeader(requestHeaders, clientHeaders, info.getRelayMode(), info.isStream());

        // 渠道头优先
        Map<String, String> channelHeaders = adaptor.setupRequestHeader(info);
        if (channelHeaders != null) {
            channelHeaders.forEach(requestHeaders::put);
        }

        // 应用 Header Override（最高优先级）
        Map<String, Object> headerOverrideSource = info.getHeadersOverride();
        if (info.isUseRuntimeHeadersOverride() && info.getRuntimeHeadersOverride() != null) {
            headerOverrideSource = info.getRuntimeHeadersOverride();
        }
        Map<String, String> headerOverride = resolveHeaderOverride(
                info, headerOverrideSource, clientHeaders, info.getApiKey(), info.isChannelTest());

        // 合并所有头：基础 → 渠道 → 覆写
        requestHeaders.forEach(builder::setHeader);
        applyHeaderOverrideToRequest(builder, headerOverride);

        return doRequest(info, builder, clientHeaders);
    }

    /**
     * 执行 Form 请求（multipart）      */
    public static HttpResponse<InputStream> doFormRequest(
            IAdaptor adaptor,
            RelayInfo info,
            InputStream requestBody,
            Map<String, String> clientHeaders,
            String method) throws Exception {

        String fullRequestURL = adaptor.getRequestURL(info);
        if (fullRequestURL == null || fullRequestURL.isEmpty()) {
            throw new IOException("get request url failed: empty url");
        }
        log.debug("fullRequestURL: {}", fullRequestURL);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullRequestURL));

        // Form 请求：使用客户端 Content-Type
        String formContentType = clientHeaders != null ? clientHeaders.get("Content-Type") : null;
        if (formContentType != null) {
            builder.setHeader("Content-Type", formContentType);
        }

        byte[] formBytes = requestBody != null ? requestBody.readAllBytes() : null;
        HttpRequest.BodyPublisher bodyPublisher = formBytes != null && formBytes.length > 0
                ? HttpRequest.BodyPublishers.ofByteArray(formBytes)
                : HttpRequest.BodyPublishers.noBody();
        builder.method(method.toUpperCase(), bodyPublisher);

        applyUpstreamContentLength(builder, info);

        // 渠道头 + 基础头
        Map<String, String> channelHeaders = adaptor.setupRequestHeader(info);
        if (channelHeaders != null) {
            channelHeaders.forEach(builder::setHeader);
        }

        // Header Override
        Map<String, Object> headerOverrideSource = info.getHeadersOverride();
        if (info.isUseRuntimeHeadersOverride() && info.getRuntimeHeadersOverride() != null) {
            headerOverrideSource = info.getRuntimeHeadersOverride();
        }
        Map<String, String> headerOverride = resolveHeaderOverride(
                info, headerOverrideSource, clientHeaders, info.getApiKey(), info.isChannelTest());
        applyHeaderOverrideToRequest(builder, headerOverride);

        return doRequest(info, builder, clientHeaders);
    }

    /**
     * 执行 WebSocket 请求      */
    public static WebSocket doWssRequest(IAdaptor adaptor, RelayInfo info, Map<String, String> clientHeaders) throws Exception {
        return doWssRequest(adaptor, info, clientHeaders, new WebSocket.Listener() {
        });
    }

    /**
     * 执行 WebSocket 请求。      */
    public static WebSocket doWssRequest(IAdaptor adaptor, RelayInfo info,
                                         Map<String, String> clientHeaders,
                                         WebSocket.Listener listener) throws Exception {
        String fullRequestURL = adaptor.getRequestURL(info);
        if (fullRequestURL == null || fullRequestURL.isEmpty()) {
            throw new ResultException(R.errorPrompt("get request url failed: empty url"));
        }

        Map<String, String> requestHeaders = adaptor.setupRequestHeader(info);
        if (requestHeaders == null) {
            requestHeaders = new LinkedHashMap<>();
        } else {
            requestHeaders = new LinkedHashMap<>(requestHeaders);
        }

        Map<String, Object> headerOverrideSource = info.getHeadersOverride();
        if (info.isUseRuntimeHeadersOverride() && info.getRuntimeHeadersOverride() != null) {
            headerOverrideSource = info.getRuntimeHeadersOverride();
        }
        Map<String, String> headerOverride = resolveHeaderOverride(
                info, headerOverrideSource, clientHeaders, info.getApiKey(), info.isChannelTest());
        requestHeaders.putAll(headerOverride);

        HttpClient client = getUpstreamHttpClient(
                info.getChannelSetting() != null ? info.getChannelSetting().getProxy() : null);
        WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(30));

        String clientProtocol = clientHeaders != null ? clientHeaders.get("Sec-WebSocket-Protocol") : null;
        if (clientProtocol == null && clientHeaders != null) {
            clientProtocol = clientHeaders.get("sec-websocket-protocol");
        }
        if (clientProtocol != null && !clientProtocol.isBlank()) {
            builder.subprotocols(
                    "realtime",
                    "openai-insecure-api-key." + info.getApiKey(),
                    "openai-beta.realtime-v1"
            );
            requestHeaders.remove("Sec-WebSocket-Protocol");
            requestHeaders.remove("sec-websocket-protocol");
        } else {
            requestHeaders.putIfAbsent("openai-beta", "realtime=v1");
        }

        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            builder.header(entry.getKey(), entry.getValue());
        }

        try {
            return builder.buildAsync(URI.create(fullRequestURL), listener)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ResultException(R.errorPrompt("dial failed to " + fullRequestURL + ": " + cause.getMessage()));
        }
    }

    /**
     * 执行 Task API 请求      */
    public static HttpResponse<InputStream> doTaskApiRequest(
            IAdaptor.ITaskAdaptor adaptor,
            RelayInfo info,
            InputStream requestBody,
            Map<String, String> clientHeaders,
            String method) throws Exception {

        String fullRequestURL = adaptor.buildRequestURL(info);
        if (fullRequestURL == null || fullRequestURL.isEmpty()) {
            throw new IOException("build request url failed: empty url");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullRequestURL));

        byte[] formBytes = requestBody != null ? requestBody.readAllBytes() : null;
        HttpRequest.BodyPublisher bodyPublisher = formBytes != null && formBytes.length > 0
                ? HttpRequest.BodyPublishers.ofByteArray(formBytes)
                : HttpRequest.BodyPublishers.noBody();
        builder.method(method.toUpperCase(), bodyPublisher);

        applyUpstreamContentLength(builder, info);

        // Task 渠道自定义头
        Object headerResult = adaptor.buildRequestHeader(info);
        if (headerResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> taskHeaders = (Map<String, String>) headerResult;
            taskHeaders.forEach(builder::setHeader);
        }

        return doRequest(info, builder, clientHeaders);
    }

    /**
     * 核心 HTTP 请求执行      * <p>
     * 包含：代理 HTTP 客户端获取、SSE Ping 保活、
     * 上游 RequestId 跟踪、请求体/响应体关
     */
    private static HttpResponse<InputStream> doRequest(
            RelayInfo info,
            HttpRequest.Builder builder,
            Map<String, String> clientHeaders) throws Exception {

        String proxy = info.getChannelSetting() != null ? info.getChannelSetting().getProxy() : null;
        HttpClient client = getUpstreamHttpClient(proxy);

        // SSE Ping 保活
        ScheduledExecutorService pingExecutor = null;
        ScheduledFuture<?> pingFuture = null;

        boolean pingEnabled = GeneralSettingConfig.isPingIntervalEnabled() && !info.isDisablePing();
        long pingIntervalSeconds = GeneralSettingConfig.getPingIntervalSeconds();
        if (pingIntervalSeconds <= 0) {
            pingIntervalSeconds = RelayCommonHelper.DEFAULT_PING_INTERVAL_SECONDS;
        }

        if (info.isStream() && pingEnabled) {
            pingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-ping");
                t.setDaemon(true);
                return t;
            });
            pingFuture = pingExecutor.scheduleAtFixedRate(
                    () -> sendPingDataSafe(info),
                    pingIntervalSeconds,
                    pingIntervalSeconds,
                    TimeUnit.SECONDS);
        }

        try {
            HttpRequest request = builder.build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response == null) {
                throw new IOException("upstream response is null");
            }
            return response;
        } catch (IOException e) {
            log.error("do request failed: {}", e.getMessage());
            throw new IOException("upstream error: do request failed", e);
        } finally {
            if (pingFuture != null) {
                pingFuture.cancel(false);
            }
            if (pingExecutor != null) {
                pingExecutor.shutdownNow();
            }
            // 请求体关闭（Go 原逻辑在 defer 中关闭 req.Body 和 c.Request.Body）
        }
    }

    /**
     * 安全的 SSE Ping 发送（带超时 + panic 恢复）      * <p>
     * 通过 info.response 获取 HttpServletResponse，写入 SSE Ping 数据
     */
    private static void sendPingDataSafe(RelayInfo info) {
        try {
            if (info != null && info.getResponse() != null) {
                RelayCommonHelper.pingData(info.getResponse());
            }
        } catch (Exception e) {
            log.debug("SSE ping error, stopping pinger: {}", e.getMessage());
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 获取有效的 Header Override 源      */
    public static Map<String, Object> getEffectiveHeaderOverride(RelayInfo info) {
        if (info == null) return Collections.emptyMap();
        if (info.isUseRuntimeHeadersOverride()) {
            return sanitizeHeaderOverrideMap(info.getRuntimeHeadersOverride());
        }
        return sanitizeHeaderOverrideMap(info.getHeadersOverride());
    }

    /**
     * 清理 Header Override Map      */
    private static Map<String, Object> sanitizeHeaderOverrideMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Collections.emptyMap();
        Map<String, Object> target = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String normalizedKey = entry.getKey() != null ? entry.getKey().trim().toLowerCase() : "";
            if (normalizedKey.isEmpty()) continue;

            String normalizedValue = String.valueOf(entry.getValue()).trim();
            if (normalizedValue.isEmpty()) {
                if (isHeaderPassthroughRuleKey(normalizedKey)) {
                    target.put(normalizedKey, "");
                }
                continue;
            }
            target.put(normalizedKey, normalizedValue);
        }
        return target;
    }

    /**
     * 判断规则 key 是否为 Header Passthrough 规则（内部覆写用）      */
    static boolean isHeaderPassthroughRuleKeyForOverride(String key) {
        if (key == null) return false;
        key = key.trim().toLowerCase();
        if (key.isEmpty()) return false;
        if ("*".equals(key)) return true;
        return key.startsWith("re:") || key.startsWith("regex:");
    }
}
