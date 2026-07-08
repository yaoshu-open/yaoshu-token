package yaoshu.token.relay.channel.task.common;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Task 渠道公共工具  * <p>
 * 提供任务渠道共用的元数据反序列化、默认值、TaskID 编解码等工具。
 */
@Slf4j
public final class TaskCommonHelper {    private TaskCommonHelper() {
    }

    /** 进度常量 */
    public static final String PROGRESS_SUBMITTED = "10%";
    public static final String PROGRESS_QUEUED = "20%";
    public static final String PROGRESS_IN_PROGRESS = "30%";
    public static final String PROGRESS_COMPLETE = "100%";

    /**
     * 元数据反序列化      * <p>
     * 通过 JSON 往返将 Map 转为目标类型。
     * 防止 metadata 覆盖 model 字段以避免计费绕过。
     */
    public static <T> T unmarshalMetadata(Map<String, Object> metadata, Class<T> targetClass) throws Exception {
        if (metadata == null) return null;
        // 防止 metadata 覆盖 model 字段
        metadata.remove("model");
        byte[] metaBytes = Convert.toJSONString(metadata).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return Convert.toJavaBean(metaBytes, targetClass);
    }

    /**
     * 返回 val 如果非空，否则返回 fallback      */
    public static String defaultString(String val, String fallback) {
        return (val == null || val.isEmpty()) ? fallback : val;
    }

    /**
     * 返回 val 如果非零，否则返回 fallback      */
    public static int defaultInt(int val, int fallback) {
        return val == 0 ? fallback : val;
    }

    /**
     * 将上游 operation name 编码为 URL 安全的 base64 字符串      * <p>
     * 用于 Gemini/Vertex 将上游 name 存储为 task ID。
     */
    public static String encodeLocalTaskID(String name) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码 base64 编码的上游 operation name      */
    public static String decodeLocalTaskID(String id) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(id);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * 构建视频代理 URL      * <p>
     * e.g., "https://your-server.com/v1/videos/task_xxxx/content"
     */
    public static String buildProxyURL(String serverAddress, String taskID) {
        return serverAddress + "/v1/videos/" + taskID + "/content";
    }
}
