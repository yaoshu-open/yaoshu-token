package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.pojo.entity.Channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ollama 渠道管理服务  * PullOllamaModel / PullOllamaModelStream / DeleteOllamaModel / FetchOllamaVersion。
 * <p>
 * 使用 java.net.http.HttpClient（JDK 内置）调用上游 Ollama HTTP API：
 * <ul>
 *   <li>POST /api/pull —— 拉取模型（流式返回 NDJSON 进度，每行一个 JSON）</li>
 *   <li>DELETE /api/delete —— 删除模型</li>
 *   <li>GET /api/version —— 查询服务版本</li>
 * </ul>
 * 流式拉取场景下，本服务把上游 NDJSON 流逐行转写为下游 SSE（{@code data: ...\n\n}），
 * 与 Go 版 OllamaPullModelStream 行为完全一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    /** 非流式拉取超时：30 分钟（与 Go 版一致） */
    private static final Duration PULL_TIMEOUT = Duration.ofMinutes(30);
    /** 流式拉取超时：1 小时（与 Go 版一致） */
    private static final Duration PULL_STREAM_TIMEOUT = Duration.ofHours(1);
    /** 版本查询超时：10 秒（与 Go 版一致） */
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(10);
    /** 删除操作超时：与 Go 版未设超时一致使用合理默认值 */
    private static final Duration DELETE_TIMEOUT = Duration.ofMinutes(5);

    private final ChannelService channelService;

    /**
     * 非流式拉取模型。      */
    public Result<Map<String, Object>> pullModel(int channelId, String modelName) {
        ChannelEndpoint ep = resolveOllamaEndpoint(channelId);
        if (ep.errorMessage != null) throw new ResultException(R.errorPrompt(ep.errorMessage));

        String url = ep.baseURL + "/api/pull";
        String body = Convert.toJSONString(Map.of(
                "name", modelName,
                "stream", false
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(PULL_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (ep.apiKey != null && !ep.apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + ep.apiKey);
        }

        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ResultException(R.errorPrompt("Failed to pull model: " + response.statusCode() + ": " + response.body()));
            }
            return R.success(Map.of("message", "Model " + modelName + " pulled successfully"));
        } catch (Exception e) {
            log.warn("Ollama pullModel 请求失败 channelId={} model={}", channelId, modelName, e);
            throw new ResultException(R.errorPrompt("Failed to pull model: " + e.getMessage()));
        }
    }

    /**
     * 流式拉取模型。      * 把上游 NDJSON 进度逐行转写为下游 SSE，并在结束时附加 {@code data: [DONE]\n\n}。
     */
    public void pullModelStream(int channelId, String modelName, HttpServletResponse response) {
        // 设置 SSE 头部（与 Go 版完全一致）
        response.setContentType("text/event-stream");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter writer;
        try {
            writer = response.getWriter();
        } catch (IOException e) {
            log.warn("Ollama pullModelStream 获取响应 Writer 失败 channelId={}", channelId, e);
            return;
        }

        ChannelEndpoint ep = resolveOllamaEndpoint(channelId);
        if (ep.errorMessage != null) {
            writer.write("data: " + Convert.toJSONString(Map.of("error", ep.errorMessage)) + "\n\n");
            writer.write("data: [DONE]\n\n");
            writer.flush();
            return;
        }

        String url = ep.baseURL + "/api/pull";
        String body = Convert.toJSONString(Map.of(
                "name", modelName,
                "stream", true
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(PULL_STREAM_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (ep.apiKey != null && !ep.apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + ep.apiKey);
        }

        boolean successful = false;
        try {
            HttpResponse<InputStream> upstream = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (upstream.statusCode() != 200) {
                String errBody = new String(upstream.body().readAllBytes(), StandardCharsets.UTF_8);
                writer.write("data: " + Convert.toJSONString(Map.of(
                        "error", "拉取模型失败 " + upstream.statusCode() + ": " + errBody
                )) + "\n\n");
                writer.write("data: [DONE]\n\n");
                writer.flush();
                return;
            }

            // 逐行读取上游 NDJSON 流，每行原样作为 SSE data 转写给下游
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(upstream.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    // 把当前进度行作为 SSE data 发送给前端
                    writer.write("data: " + line + "\n\n");
                    writer.flush();

                    // 解析进度，识别 success/error 终止条件
                    Map<String, Object> progress;
                    try {
                        progress = Convert.toJSONObject(line);
                    } catch (Exception ignore) {
                        continue; // 忽略解析失败的行，与 Go 版行为一致
                    }
                    if (progress == null) continue;
                    Object status = progress.get("status");
                    if (status instanceof String s) {
                        if ("error".equalsIgnoreCase(s)) {
                            writer.write("data: " + Convert.toJSONString(Map.of(
                                    "error", "拉取模型失败: " + line
                            )) + "\n\n");
                            writer.flush();
                            writer.write("data: [DONE]\n\n");
                            writer.flush();
                            return;
                        }
                        if ("success".equalsIgnoreCase(s)) {
                            successful = true;
                            break;
                        }
                    }
                }
            }

            if (successful) {
                writer.write("data: " + Convert.toJSONString(Map.of(
                        "message", "Model " + modelName + " pulled successfully"
                )) + "\n\n");
            } else {
                writer.write("data: " + Convert.toJSONString(Map.of(
                        "error", "拉取模型未完成: 未收到成功状态"
                )) + "\n\n");
            }
        } catch (Exception e) {
            log.warn("Ollama pullModelStream 请求失败 channelId={} model={}", channelId, modelName, e);
            writer.write("data: " + Convert.toJSONString(Map.of(
                    "error", "请求失败: " + e.getMessage()
            )) + "\n\n");
        } finally {
            writer.write("data: [DONE]\n\n");
            writer.flush();
        }
    }

    /**
     * 删除模型。      */
    public Result<Map<String, Object>> deleteModel(int channelId, String modelName) {
        ChannelEndpoint ep = resolveOllamaEndpoint(channelId);
        if (ep.errorMessage != null) throw new ResultException(R.errorPrompt(ep.errorMessage));

        String url = ep.baseURL + "/api/delete";
        String body = Convert.toJSONString(Map.of("name", modelName));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(DELETE_TIMEOUT)
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (ep.apiKey != null && !ep.apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + ep.apiKey);
        }

        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ResultException(R.errorPrompt("Failed to delete model: " + response.statusCode() + ": " + response.body()));
            }
            return R.success(Map.of("message", "Model " + modelName + " deleted successfully"));
        } catch (Exception e) {
            log.warn("Ollama deleteModel 请求失败 channelId={} model={}", channelId, modelName, e);
            throw new ResultException(R.errorPrompt("Failed to delete model: " + e.getMessage()));
        }
    }

    /**
     * 查询 Ollama 服务版本。      * 失败时返回 success=false（与 Go 版返回 200+success:false 行为一致）。
     */
    public Result<Map<String, Object>> fetchVersion(int channelId) {
        ChannelEndpoint ep = resolveOllamaEndpoint(channelId);
        if (ep.errorMessage != null) throw new ResultException(R.errorPrompt(ep.errorMessage));

        String trimmedBase = ep.baseURL.endsWith("/")
                ? ep.baseURL.substring(0, ep.baseURL.length() - 1)
                : ep.baseURL;
        if (trimmedBase.isEmpty()) {
            throw new ResultException(R.errorPrompt("获取Ollama版本失败: baseURL 为空"));
        }
        String url = trimmedBase + "/api/version";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(VERSION_TIMEOUT)
                .GET();
        if (ep.apiKey != null && !ep.apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + ep.apiKey);
        }

        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ResultException(R.errorPrompt("获取Ollama版本失败: 查询版本失败 " + response.statusCode() + ": " + response.body()));
            }
            Map<String, Object> versionResp;
            try {
                versionResp = Convert.toJSONObject(response.body());
            } catch (Exception e) {
                throw new ResultException(R.errorPrompt("获取Ollama版本失败: 解析响应失败: " + e.getMessage()));
            }
            if (versionResp == null) {
                throw new ResultException(R.errorPrompt("获取Ollama版本失败: 解析响应失败"));
            }
            Object version = versionResp.get("version");
            if (version == null || version.toString().isEmpty()) {
                throw new ResultException(R.errorPrompt("获取Ollama版本失败: 未返回版本信息"));
            }
            return R.success(Map.of("version", version.toString()));
        } catch (Exception e) {
            log.warn("Ollama fetchVersion 请求失败 channelId={}", channelId, e);
            throw new ResultException(R.errorPrompt("获取Ollama版本失败: " + e.getMessage()));
        }
    }

    /**
     * 解析渠道并返回 Ollama 端点信息。返回结构中 errorResult 非空表示前置校验失败。
     */
    private ChannelEndpoint resolveOllamaEndpoint(int channelId) {
        ChannelEndpoint ep = new ChannelEndpoint();
        Channel channel = channelService.getById(channelId);
        if (channel == null) {
            ep.errorMessage = "Channel not found";
            return ep;
        }
        if (channel.getType() == null || channel.getType() != ChannelConstants.CHANNEL_TYPE_OLLAMA) {
            ep.errorMessage = "This operation is only supported for Ollama channels";
            return ep;
        }

        // baseURL 兜底：channel.baseUrl 为空时取 ChannelConstants 中的默认值
        String baseURL = channel.getBaseUrl();
        if (baseURL == null || baseURL.isEmpty()) {
            int idx = channel.getType();
            if (idx >= 0 && idx < ChannelConstants.CHANNEL_BASE_URLS.size()) {
                baseURL = ChannelConstants.CHANNEL_BASE_URLS.get(idx);
            }
        }
        if (baseURL == null || baseURL.isEmpty()) {
            ep.errorMessage = "Ollama 渠道 baseURL 未配置";
            return ep;
        }
        ep.baseURL = baseURL;

        // key 处理：取第一行（兼容多 key）
        String rawKey = channel.getKey();
        if (rawKey != null) {
            int newline = rawKey.indexOf('\n');
            ep.apiKey = newline >= 0 ? rawKey.substring(0, newline) : rawKey;
        }
        return ep;
    }

    /** Ollama 渠道访问端点解析结果 */
    private static class ChannelEndpoint {
        String baseURL;
        String apiKey;
        String errorMessage;
    }
}
