package yaoshu.token.controller;

import ai.yue.library.base.convert.Convert;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.ContextKeyConstants;
import yaoshu.token.constant.TaskConstants;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.pojo.entity.Task;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.handler.TaskRelayService;
import yaoshu.token.service.ChannelCacheService;
import yaoshu.token.service.DownloadService;
import yaoshu.token.service.TaskService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 视频代理 & 视频中转控制器
 * <p>
 * 合并 Go controller/video_proxy.go + video_proxy_gemini.go + swag_video.go
 * 认证：所有 /v1/* 端点统一走 TokenAuthFilter（API Key 认证）
 * <p>
 * 设计说明：Go 原版 video content 端点用 TokenOrUserAuth（同时支持 Session 与 API Key），
 * Java 翻译后已统一为 TokenAuth 单通道，TokenOrUserAuthFilter 已删除（死代码，从未注册）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class VideoController {    private final TaskService taskService;
    private final TaskRelayService taskRelayService;

    /** 任务成功状态*/
    private static final String TASK_STATUS_SUCCESS = "success";

    // ======================== Video Proxy ========================

    /**
     * 获取视频内容（任务 ID）      * <p>
     * Go 原版用 TokenOrUserAuth（Session+API Key 双通道），Java 统一走 TokenAuthFilter（API Key 单通道）。
     */
    @GetMapping("/v1/videos/{task_id}/content")
    public void getContent(@PathVariable("task_id") String taskId,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException {
        if (taskId == null || taskId.isEmpty()) {
            videoProxyError(response, 400, "invalid_request_error", "task_id is required");
            return;
        }

        Integer userIdObj = (Integer) request.getAttribute("id");
        if (userIdObj == null) {
            videoProxyError(response, 401, "authentication_error", "user not authenticated");
            return;
        }
        int userId = userIdObj;

        // 1. 查询任务
        Task task = taskService.getByTaskId(userId, taskId);
        if (task == null) {
            log.warn("Task not found: userId={}, taskId={}", userId, taskId);
            videoProxyError(response, 404, "invalid_request_error", "Task not found");
            return;
        }

        if (!TASK_STATUS_SUCCESS.equals(task.getStatus())) {
            videoProxyError(response, 400, "invalid_request_error",
                    "Task is not completed yet, current status: " + task.getStatus());
            return;
        }

        // 2. 获取渠道
        Channel channel = ChannelCacheService.cacheGetChannel(task.getChannelId());
        if (channel == null) {
            log.error("Channel not found for task {}: channelId={}", taskId, task.getChannelId());
            videoProxyError(response, 500, "server_error", "Failed to retrieve channel information");
            return;
        }

        String baseUrl = channel.getBaseUrl() != null ? channel.getBaseUrl() : "https://api.openai.com";

        // 3. 创建 HTTP 代理客户端
        HttpClient client = buildHttpClient(channel.getSetting());

        // 4. 按渠道类型解析视频 URL
        String videoUrl;
        switch (channel.getType()) {
            case ChannelConstants.CHANNEL_TYPE_GEMINI -> {
                String apiKey = getTaskPrivateDataKey(task);
                if (apiKey == null || apiKey.isEmpty()) {
                    log.error("Missing stored API key for Gemini task {}", taskId);
                    videoProxyError(response, 500, "server_error", "API key not stored for task");
                    return;
                }
                videoUrl = getGeminiVideoURL(channel, task, apiKey, client);
                if (videoUrl == null) {
                    log.error("Failed to resolve Gemini video URL for task {}", taskId);
                    videoProxyError(response, 502, "server_error", "Failed to resolve Gemini video URL");
                    return;
                }
            }
            case ChannelConstants.CHANNEL_TYPE_VERTEX_AI -> {
                videoUrl = getVertexVideoURL(channel, task, client);
                if (videoUrl == null) {
                    log.error("Failed to resolve Vertex video URL for task {}", taskId);
                    videoProxyError(response, 502, "server_error", "Failed to resolve Vertex video URL");
                    return;
                }
            }
            case ChannelConstants.CHANNEL_TYPE_OPENAI,
                 ChannelConstants.CHANNEL_TYPE_SORA -> {
                videoUrl = baseUrl + "/v1/videos/" + getUpstreamTaskId(task) + "/content";
            }
            default -> {
                videoUrl = getTaskResultURL(task);
            }
        }

        videoUrl = videoUrl != null ? videoUrl.trim() : "";
        if (videoUrl.isEmpty()) {
            log.error("Video URL is empty for task {}", taskId);
            videoProxyError(response, 502, "server_error", "Failed to fetch video content");
            return;
        }

        // 5. Handle data: URL
        if (videoUrl.startsWith("data:")) {
            try {
                writeVideoDataURL(response, videoUrl);
            } catch (Exception e) {
                log.error("Failed to decode video data URL for task {}: {}", taskId, e.getMessage());
                videoProxyError(response, 502, "server_error", "Failed to fetch video content");
            }
            return;
        }

        // 6. SSRF 校验
        // 复用 DownloadService 的 SSRF 防护：读取 fetch_setting 配置校验 URL，被拦截抛异常
        try {
            DownloadService.validateURL(videoUrl);
        } catch (Exception e) {
            log.warn("Video URL blocked for task {}: {}", taskId, e.getMessage());
            videoProxyError(response, 403, "server_error", "request blocked: " + e.getMessage());
            return;
        }

        // 7. 代理请求上游视频
        try {
            HttpRequest proxyReq = HttpRequest.newBuilder()
                    .uri(URI.create(videoUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            // 特殊渠道添加认证头
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(videoUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET();
            if (channel.getType() == ChannelConstants.CHANNEL_TYPE_GEMINI) {
                String apiKey = getTaskPrivateDataKey(task);
                if (apiKey != null && !apiKey.isEmpty()) {
                    reqBuilder.header("x-goog-api-key", apiKey);
                }
            } else if (channel.getType() == ChannelConstants.CHANNEL_TYPE_OPENAI
                    || channel.getType() == ChannelConstants.CHANNEL_TYPE_SORA) {
                reqBuilder.header("Authorization", "Bearer " + channel.getKey());
            }
            HttpRequest upstreamReq = reqBuilder.build();

            HttpResponse<InputStream> upstreamResp = client.send(upstreamReq, HttpResponse.BodyHandlers.ofInputStream());

            if (upstreamResp.statusCode() != 200) {
                log.error("Upstream returned status {} for {}", upstreamResp.statusCode(), videoUrl);
                videoProxyError(response, 502, "server_error",
                        "Upstream service returned status " + upstreamResp.statusCode());
                return;
            }

            // 拷贝上游响应头
            upstreamResp.headers().map().forEach((key, values) -> {
                for (String value : values) {
                    response.addHeader(key, value);
                }
            });
            response.setHeader("Cache-Control", "public, max-age=86400");
            response.setStatus(200);

            // 流式拷贝响应体
            try (InputStream body = upstreamResp.body();
                 OutputStream out = response.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = body.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }
        } catch (IOException e) {
            log.error("Failed to fetch video from {}: {}", videoUrl, e.getMessage());
            videoProxyError(response, 502, "server_error", "Failed to fetch video content");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Video fetch interrupted for {}", videoUrl);
            videoProxyError(response, 502, "server_error", "Failed to fetch video content");
        }
    }

    // ======================== Gemini Video URL 解析 ========================

    /**
     * 获取 Gemini 视频 URL      */
    private String getGeminiVideoURL(Channel channel, Task task, String apiKey, HttpClient client) {
        // 1. 尝试从 task.Data 提取
        String url = extractGeminiVideoURLFromTaskData(task);
        if (url != null && !url.isEmpty()) {
            return ensureAPIKey(url, apiKey);
        }

        // 2. 通过 Gemini Task API 查询
        String baseUrl = channel.getBaseUrl() != null ? channel.getBaseUrl() : "";
        if (baseUrl.isEmpty()) {
            baseUrl = "https://generativelanguage.googleapis.com";
        }
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("task_id", getUpstreamTaskId(task));
            params.put("action", task.getAction());
            String body = Convert.toJSONString(params);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1beta/models/gemini-2.5-flash:fetchPredictOperation"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = Convert.toJSONObject(resp.body());
                // 尝试解析 taskInfo.RemoteUrl
                if (result != null) {
                    String remoteUrl = extractRemoteUrl(result);
                    if (remoteUrl != null && !remoteUrl.isEmpty()) {
                        return ensureAPIKey(remoteUrl, apiKey);
                    }
                }
                // 回退：从响应中提取 URI
                url = extractGeminiVideoURLFromMap(result);
                if (url != null && !url.isEmpty()) {
                    return ensureAPIKey(url, apiKey);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Gemini task result: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractRemoteUrl(Map<String, Object> result) {
        // 尝试 navigator 常见的 TaskInfo 字段
        Object taskInfo = result.get("taskInfo");
        if (taskInfo instanceof Map) {
            Object remoteUrl = ((Map<String, Object>) taskInfo).get("remoteUrl");
            if (remoteUrl instanceof String s && !s.isEmpty()) return s;
        }
        // 尝试 response.generateVideoResponse.generatedSamples[0].video.uri
        return extractGeminiVideoURLFromMap(result);
    }

    private String extractGeminiVideoURLFromTaskData(Task task) {
        if (task.getData() == null || task.getData().isEmpty()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = Convert.toJSONObject(task.getData());
            return extractGeminiVideoURLFromMap(payload);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiVideoURLFromMap(Map<String, Object> payload) {
        if (payload == null) return null;

        // uri 顶层
        if (payload.get("uri") instanceof String s && !s.isEmpty()) return s;

        // response.videos[0].uri
        Object response = payload.get("response");
        if (response instanceof Map resp) {
            String uri = extractGeminiVideoURLFromResponse(resp);
            if (uri != null) return uri;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiVideoURLFromResponse(Map<String, Object> resp) {
        if (resp == null) return null;

        // response.generateVideoResponse.generatedSamples[0].video.uri
        Object gvr = resp.get("generateVideoResponse");
        if (gvr instanceof Map gvrMap) {
            Object samples = gvrMap.get("generatedSamples");
            if (samples instanceof List sampleList) {
                for (Object sample : sampleList) {
                    if (sample instanceof Map sm) {
                        Object video = sm.get("video");
                        if (video instanceof Map vm) {
                            Object uri = vm.get("uri");
                            if (uri instanceof String s && !s.isEmpty()) return s;
                        }
                    }
                }
            }
        }

        // response.videos[0].uri
        Object videos = resp.get("videos");
        if (videos instanceof List videoList) {
            for (Object v : videoList) {
                if (v instanceof Map vm) {
                    Object uri = vm.get("uri");
                    if (uri instanceof String s && !s.isEmpty()) return s;
                }
            }
        }

        // response.video / response.uri
        if (resp.get("video") instanceof String s && !s.isEmpty()) return s;
        if (resp.get("uri") instanceof String s && !s.isEmpty()) return s;

        return null;
    }

    // ======================== Vertex Video URL 解析 ========================

    /**
     * 获取 Vertex AI 视频 URL      */
    private String getVertexVideoURL(Channel channel, Task task, HttpClient client) {
        // 1. 尝试 ResultURL
        String url = getTaskResultURL(task);
        if (url != null && !url.isEmpty() && !isTaskProxyContentURL(url, task.getTaskId())) {
            return url;
        }

        // 2. 尝试从 task.Data 提取
        url = extractVertexVideoURLFromTaskData(task);
        if (url != null && !url.isEmpty()) return url;

        // 3. 通过 Vertex Task API 查询
        String key = getVertexTaskKey(channel, task);
        if (key == null || key.isEmpty()) {
            log.error("Vertex key not available for task {}", task.getTaskId());
            return null;
        }

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("task_id", getUpstreamTaskId(task));
            params.put("action", task.getAction());
            String body = Convert.toJSONString(params);

            // Vertex AI 使用 discovery API
            String fetchUrl = "https://aiplatform.googleapis.com/v1/projects/-/locations/-/publishers/google/models/veo-3.1-generate-preview:fetchPredictOperation";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(fetchUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = Convert.toJSONObject(resp.body());
                url = extractVertexVideoURLFromMap(result);
                if (url != null && !url.isEmpty()) return url;
            }
        } catch (Exception e) {
            log.error("Failed to fetch Vertex task result: {}", e.getMessage());
        }
        return null;
    }

    private String getVertexTaskKey(Channel channel, Task task) {
        if (task != null) {
            String key = getTaskPrivateDataKey(task);
            if (key != null && !key.isEmpty()) return key;
        }
        if (channel != null && channel.getKeys() != null) {
            for (String k : channel.getKeys()) {
                if (k != null && !k.isBlank()) return k.trim();
            }
        }
        return channel != null ? channel.getKey() : null;
    }

    private String extractVertexVideoURLFromTaskData(Task task) {
        if (task.getData() == null || task.getData().isEmpty()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = Convert.toJSONObject(task.getData());
            return extractVertexVideoURLFromMap(payload);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractVertexVideoURLFromMap(Map<String, Object> payload) {
        if (payload == null) return null;
        Object response = payload.get("response");
        if (!(response instanceof Map resp)) return null;

        // response.videos[0].bytesBase64Encoded
        Object videos = resp.get("videos");
        if (videos instanceof List videoList && !videoList.isEmpty()) {
            Object first = videoList.get(0);
            if (first instanceof Map video) {
                Object b64 = video.get("bytesBase64Encoded");
                if (b64 instanceof String s && !s.isBlank()) {
                    String mime = video.get("mimeType") instanceof String ms ? ms : null;
                    String enc = video.get("encoding") instanceof String es ? es : null;
                    return buildVideoDataURL(mime, enc, s.trim());
                }
            }
        }

        // response.bytesBase64Encoded
        Object b64 = resp.get("bytesBase64Encoded");
        if (b64 instanceof String s && !s.isBlank()) {
            String enc = resp.get("encoding") instanceof String es ? es : null;
            return buildVideoDataURL(null, enc, s.trim());
        }

        // response.video
        Object video = resp.get("video");
        if (video instanceof String s && !s.isBlank()) {
            if (s.startsWith("data:") || s.startsWith("http://") || s.startsWith("https://")) {
                return s;
            }
            String enc = resp.get("encoding") instanceof String es ? es : null;
            return buildVideoDataURL(null, enc, s);
        }
        return null;
    }

    // ======================== 工具方法 ========================

    /**
     * 构建 data: URL      */
    private static String buildVideoDataURL(String mimeType, String encoding, String base64Data) {
        String mime = mimeType != null ? mimeType.trim() : "";
        if (mime.isEmpty()) {
            String enc = encoding != null ? encoding.trim() : "";
            if (enc.isEmpty()) enc = "mp4";
            mime = enc.contains("/") ? enc : "video/" + enc;
        }
        return "data:" + mime + ";base64," + base64Data;
    }

    /**
     * 为 URI 附加 API Key 查询参数      */
    private static String ensureAPIKey(String uri, String key) {
        if (key == null || key.isEmpty() || uri == null || uri.isEmpty()) return uri;
        if (uri.contains("key=")) return uri;
        return uri.contains("?") ? uri + "&key=" + key : uri + "?key=" + key;
    }

    /**
     * 判断 URL 是否为代理内容 URL      */
    private static boolean isTaskProxyContentURL(String url, String taskId) {
        if (url == null || url.isEmpty() || taskId == null || taskId.isEmpty()) return false;
        return url.contains("/v1/videos/" + taskId + "/content");
    }

    /**
     * 获取任务上游 Task ID      */
    private static String getUpstreamTaskId(Task task) {
        // 上游 Task ID 可能存储在 properties JSON 中，简化实现：直接用 task_id
        if (task.getProperties() != null && !task.getProperties().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> props = Convert.toJSONObject(task.getProperties());
                Object upstreamId = props.get("upstream_task_id");
                if (upstreamId instanceof String s && !s.isEmpty()) return s;
            } catch (Exception e) {
                log.debug("解析 task properties 获取 upstream_task_id 失败", e);
            }
        }
        return task.getTaskId();
    }

    /**
     * 获取任务结果 URL      */
    private static String getTaskResultURL(Task task) {
        // private_data JSON 中解析 ResultURL，回退到 fail_reason（旧数据兼容）
        if (task.getPrivateData() != null && !task.getPrivateData().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> pd = Convert.toJSONObject(task.getPrivateData());
                Object url = pd.get("ResultURL");
                if (url instanceof String s && !s.isEmpty()) return s;
            } catch (Exception e) {
                log.debug("解析 task private_data 获取 ResultURL 失败", e);
            }
        }
        return task.getFailReason();
    }

    /**
     * 从 Task.privateData JSON 中提取 API Key      */
    private static String getTaskPrivateDataKey(Task task) {
        if (task.getPrivateData() == null || task.getPrivateData().isEmpty()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> pd = Convert.toJSONObject(task.getPrivateData());
            Object key = pd.get("Key");
            if (key instanceof String s && !s.isEmpty()) return s;
        } catch (Exception e) {
            log.debug("解析 task private_data 获取 Key 失败", e);
        }
        return null;
    }

    /**
     * 根据渠道 Setting 构建带代理的 HttpClient      */
    private static HttpClient buildHttpClient(String channelSetting) {
        var builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);

        // 解析 channelSetting JSON 中的 proxy 字段，配置 HttpClient 代理
        String proxy = parseProxyFromSetting(channelSetting);
        if (proxy != null && !proxy.isEmpty()) {
            try {
                String proxyUrl = proxy.trim();
                // 支持 http(s)://host:port 与裸 host:port 两种格式
                if (proxyUrl.startsWith("http://") || proxyUrl.startsWith("https://")
                        || proxyUrl.startsWith("socks5://") || proxyUrl.startsWith("socks5h://")) {
                    URI proxyUri = URI.create(proxyUrl);
                    builder.proxy(java.net.ProxySelector.of(
                            new java.net.InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())));
                } else {
                    String[] parts = proxyUrl.split(":");
                    if (parts.length == 2) {
                        builder.proxy(java.net.ProxySelector.of(
                                new java.net.InetSocketAddress(parts[0], Integer.parseInt(parts[1]))));
                    }
                }
            } catch (Exception e) {
                log.warn("解析渠道代理配置失败，使用直连: {}", e.getMessage());
            }
        }
        return builder.build();
    }

    /** 从渠道 Setting JSON 解析 proxy 字段*/
    private static String parseProxyFromSetting(String channelSetting) {
        if (channelSetting == null || channelSetting.isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> setting = Convert.toJSONObject(channelSetting);
            Object proxy = setting.get("proxy");
            return proxy instanceof String s ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 处理 data: URL 的视频写入      */
    private static void writeVideoDataURL(HttpServletResponse response, String dataUrl) throws Exception {
        int commaIdx = dataUrl.indexOf(',');
        if (commaIdx < 0) throw new IllegalArgumentException("invalid data url");

        String header = dataUrl.substring(0, commaIdx);
        String payload = dataUrl.substring(commaIdx + 1);
        if (!header.startsWith("data:") || !header.contains(";base64")) {
            throw new IllegalArgumentException("unsupported data url");
        }

        String mimeType = header.substring("data:".length());
        int semiIdx = mimeType.indexOf(';');
        if (semiIdx >= 0) mimeType = mimeType.substring(0, semiIdx);
        if (mimeType.isEmpty()) mimeType = "video/mp4";

        byte[] videoBytes;
        try {
            videoBytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            // 尝试无填充的 Base64
            videoBytes = Base64.getMimeDecoder().decode(payload);
        }

        response.setHeader("Content-Type", mimeType);
        response.setHeader("Cache-Control", "public, max-age=86400");
        response.setContentLength(videoBytes.length);
        response.setStatus(200);
        response.getOutputStream().write(videoBytes);
    }

    /**
     * 返回 OpenAI 兼容格式的错误响应      */
    private static void videoProxyError(HttpServletResponse response, int status, String errType, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", Map.of("message", message, "type", errType));
        response.getOutputStream().write(Convert.toJSONString(body).getBytes(StandardCharsets.UTF_8));
    }

    // ======================== Video Generation (骨架) ========================

    @PostMapping("/v1/video/generations")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return submitVideoTask(request, body, null, null);
    }

    @GetMapping("/v1/video/generations/{task_id}")
    public Map<String, Object> fetch(@PathVariable("task_id") String taskId, HttpServletRequest request) throws Exception {
        return taskRelayService.taskFetch(request, buildTaskRelayInfo(request, Map.of()));
    }

    @PostMapping("/v1/videos/{video_id}/remix")
    public Map<String, Object> remix(@PathVariable("video_id") String videoId,
                                     @RequestBody Map<String, Object> body,
                                     HttpServletRequest request) {
        return submitVideoTask(request, body, null, TaskConstants.TASK_ACTION_REMIX);
    }

    @PostMapping("/v1/videos")
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return submitVideoTask(request, body, null, null);
    }

    @GetMapping("/v1/videos/{task_id}")
    public Map<String, Object> fetchOpenAI(@PathVariable("task_id") String taskId, HttpServletRequest request) throws Exception {
        return taskRelayService.taskFetch(request, buildTaskRelayInfo(request, Map.of()));
    }

    // ======================== Kling ========================

    @PostMapping("/kling/v1/videos/text2video")
    public Map<String, Object> klingText2Video(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return submitVideoTask(request, body, "kling", TaskConstants.TASK_ACTION_TEXT_GENERATE);
    }

    @PostMapping("/kling/v1/videos/image2video")
    public Map<String, Object> klingImage2Video(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return submitVideoTask(request, body, "kling", TaskConstants.TASK_ACTION_GENERATE);
    }

    @GetMapping("/kling/v1/videos/text2video/{task_id}")
    public Map<String, Object> klingT2VFetch(@PathVariable("task_id") String taskId, HttpServletRequest request) throws Exception {
        return taskRelayService.taskFetch(request, buildTaskRelayInfo(request, Map.of()));
    }

    @GetMapping("/kling/v1/videos/image2video/{task_id}")
    public Map<String, Object> klingI2VFetch(@PathVariable("task_id") String taskId, HttpServletRequest request) throws Exception {
        return taskRelayService.taskFetch(request, buildTaskRelayInfo(request, Map.of()));
    }

    // ======================== Jimeng ========================

    @PostMapping("/jimeng/")
    public Map<String, Object> jimeng(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return submitVideoTask(request, body, "jimeng", TaskConstants.TASK_ACTION_GENERATE);
    }

    private Map<String, Object> submitVideoTask(HttpServletRequest request, Map<String, Object> body, String platform, String action) {
        if (platform != null) {
            request.setAttribute("platform", platform);
        }
        RelayInfo info = buildTaskRelayInfo(request, body);
        if (action != null) {
            info.setTaskAction(action);
        }
        try {
            TaskRelayService.TaskSubmitResult result = taskRelayService.relayTask(request, info);
            return Map.of("id", result.getPublicTaskID(), "status", TaskConstants.TASK_STATUS_SUBMITTED);
        } catch (TaskRelayService.TaskRelayException e) {
            return e.toResponseBody();
        }
    }

    private RelayInfo buildTaskRelayInfo(HttpServletRequest request, Map<String, Object> body) {
        RelayInfo info = new RelayInfo();
        info.setStartTime(java.time.LocalDateTime.now());
        info.setRequestURLPath(request.getRequestURI());
        info.setRequest(body);
        info.setUserId(toInt(request.getAttribute(ContextKeyConstants.USER_ID)));
        info.setTokenId(toInt(request.getAttribute(ContextKeyConstants.TOKEN_ID)));
        info.setTokenGroup(stringAttr(request, ContextKeyConstants.TOKEN_GROUP));
        info.setUserGroup(stringAttr(request, ContextKeyConstants.USER_GROUP));
        info.setUsingGroup(stringAttr(request, ContextKeyConstants.USING_GROUP));
        String model = resolveModel(body, stringAttr(request, ContextKeyConstants.ORIGINAL_MODEL));
        info.setOriginModelName(model);
        info.setUpstreamModelName(model);
        return info;
    }

    private String resolveModel(Map<String, Object> body, String fallback) {
        Object model = body != null ? body.get("model") : null;
        return model != null && !String.valueOf(model).isEmpty() ? String.valueOf(model) : fallback;
    }

    private String stringAttr(HttpServletRequest request, String key) {
        Object value = request.getAttribute(key);
        return value != null ? String.valueOf(value) : null;
    }

    private int toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception e) {
                log.debug("字段值转 int 失败，返回 0: {}", value, e);
                return 0;
            }
        }
        return 0;
    }
}
