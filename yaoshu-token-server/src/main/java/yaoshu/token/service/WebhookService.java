package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Webhook 服务  * <p>
 * 核心方法：SendWebhook、BuildWebhookPayload
 * 在中转事件（如配额耗尽、任务完成）时发送 HTTP POST 通知到用户配置的 Webhook URL。
 */
@Slf4j
public class WebhookService {    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 发送 Webhook 通知      *
     * @param url     Webhook URL
     * @param payload 通知负载
     * @return 是否发送成功
     */
    public static boolean sendWebhook(String url, Map<String, Object> payload) {
        if (url == null || url.isEmpty() || payload == null) {
            return false;
        }

        try {
            byte[] body = Convert.toJSONString(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("webhook sent successfully to {}, status: {}", url, response.statusCode());
                return true;
            } else {
                log.warn("webhook sent to {} returned status: {}", url, response.statusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("failed to send webhook to {}: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * 构建 Webhook 负载      *
     * @param event   事件类型（如 "quota_exceeded", "task_completed"）
     * @param userId  用户 ID
     * @param data    事件数据
     * @return Webhook 负载 Map
     */
    public static Map<String, Object> buildWebhookPayload(String event, int userId, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("user_id", userId);
        payload.put("timestamp", System.currentTimeMillis() / 1000);
        if (data != null) {
            payload.put("data", data);
        }
        return payload;
    }
}
