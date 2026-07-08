package yaoshu.token.relay.channel.task.gemini;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Task Gemini 计费处理器  * <p>
 * 提供 Veo 视频生成任务的分辨率/时长解析与计费比率计算。
 */
@Slf4j
public class TaskGeminiBillingHandler {

    private TaskGeminiBillingHandler() {
    }

    /**
     * 从 metadata 中提取 durationSeconds      * <p>
     * 默认返回 8（Veo 默认时长）。
     */
    public static int parseVeoDurationSeconds(Map<String, Object> metadata) {
        if (metadata == null) return 8;
        Object v = metadata.get("durationSeconds");
        if (v instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        return 8;
    }

    /**
     * 从 metadata 中提取 resolution      * <p>
     * 默认返回 "720p"。
     */
    public static String parseVeoResolution(Map<String, Object> metadata) {
        if (metadata == null) return "720p";
        Object v = metadata.get("resolution");
        if (v instanceof String s && !s.isEmpty()) {
            return s.toLowerCase();
        }
        return "720p";
    }

    /**
     * 解析有效时长      * <p>
     * 优先级：metadata["durationSeconds"] > stdDuration > stdSeconds > default(8)
     */
    public static int resolveVeoDuration(Map<String, Object> metadata, int stdDuration, String stdSeconds) {
        if (metadata != null && metadata.containsKey("durationSeconds")) {
            int d = parseVeoDurationSeconds(metadata);
            if (d > 0) return d;
        }
        if (stdDuration > 0) return stdDuration;
        if (stdSeconds != null) {
            try {
                int s = Integer.parseInt(stdSeconds);
                if (s > 0) return s;
            } catch (NumberFormatException ignored) {
            }
        }
        return 8;
    }

    /**
     * 解析有效分辨率      * <p>
     * 优先级：metadata["resolution"] > sizeToVeoResolution(stdSize) > default("720p")
     */
    public static String resolveVeoResolution(Map<String, Object> metadata, String stdSize) {
        if (metadata != null && metadata.containsKey("resolution")) {
            String r = parseVeoResolution(metadata);
            if (!r.isEmpty()) return r;
        }
        if (stdSize != null && !stdSize.isEmpty()) {
            return sizeToVeoResolution(stdSize);
        }
        return "720p";
    }

    /**
     * 将 "WxH" 尺寸转换为 Veo 分辨率标签      */
    public static String sizeToVeoResolution(String size) {
        if (size == null) return "720p";
        String[] parts = size.toLowerCase().split("x");
        if (parts.length != 2) return "720p";
        try {
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);
            int maxDim = Math.max(w, h);
            if (maxDim >= 3840) return "4k";
            if (maxDim >= 1920) return "1080p";
            return "720p";
        } catch (NumberFormatException e) {
            return "720p";
        }
    }

    /**
     * 将 "WxH" 尺寸转换为 Veo 宽高比      */
    public static String sizeToVeoAspectRatio(String size) {
        if (size == null) return "16:9";
        String[] parts = size.toLowerCase().split("x");
        if (parts.length != 2) return "16:9";
        try {
            int w = Integer.parseInt(parts[0]);
            int h = Integer.parseInt(parts[1]);
            if (w <= 0 || h <= 0) return "16:9";
            return h > w ? "9:16" : "16:9";
        } catch (NumberFormatException e) {
            return "16:9";
        }
    }

    /**
     * 返回给定分辨率的计费倍率      * <p>
     * 标准分辨率（720p, 1080p）返回 1.0。
     * 4K 返回基于 Vertex AI 官方定价的模型特定倍率。
     */
    public static double veoResolutionRatio(String modelName, String resolution) {
        if (!"4k".equals(resolution)) {
            return 1.0;
        }
        if (modelName == null) return 1.0;
        if (modelName.contains("3.1-fast-generate")) {
            return 2.333333;
        }
        if (modelName.contains("3.1-generate") || modelName.contains("3.1")) {
            return 1.5;
        }
        return 1.0;
    }
}
