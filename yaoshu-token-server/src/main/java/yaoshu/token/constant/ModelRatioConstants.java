package yaoshu.token.constant;

/**
 * 模型倍率常量  * <p>
 * 包含汇率常量（USD2RMB、USD、RMB）和默认模型倍率映射。
 * 因模型列表较长（~150 条），仅保留高频模型的倍率映射。
 * 完整模型列表由运营后台 JSON 配置维护。
 */
public final class ModelRatioConstants {

    private ModelRatioConstants() {
    }

    /** 暂定 1 USD = 7.3 RMB */
    public static final double USD2RMB = 7.3;
    /** $0.002 = 1 → $1 = 500 */
    public static final double USD = 500;
    /** RMB = USD / USD2RMB */
    public static final double RMB = USD / USD2RMB;

    /** 默认模型倍率（1 === $0.002 / 1K tokens） */
    public static final java.util.Map<String, Double> DEFAULT_MODEL_RATIO = java.util.Map.ofEntries(
            java.util.Map.entry("gpt-4-gizmo-*", 15.0),
            java.util.Map.entry("gpt-4o-gizmo-*", 2.5),
            java.util.Map.entry("gpt-4-all", 15.0),
            java.util.Map.entry("gpt-4o-all", 15.0),
            java.util.Map.entry("gpt-4", 15.0),
            java.util.Map.entry("gpt-4-0613", 15.0),
            java.util.Map.entry("gpt-4-32k", 30.0),
            java.util.Map.entry("gpt-4-32k-0613", 30.0),
            java.util.Map.entry("gpt-4-1106-preview", 5.0),
            java.util.Map.entry("gpt-4-0125-preview", 5.0),
            java.util.Map.entry("gpt-4-turbo-preview", 5.0),
            java.util.Map.entry("gpt-4-vision-preview", 5.0),
            java.util.Map.entry("gpt-4-1106-vision-preview", 5.0),
            java.util.Map.entry("chatgpt-4o-latest", 2.5),
            java.util.Map.entry("gpt-4o", 1.25),
            java.util.Map.entry("gpt-4o-audio-preview", 1.25),
            java.util.Map.entry("gpt-4o-2024-05-13", 2.5),
            java.util.Map.entry("gpt-4o-2024-08-06", 1.25),
            java.util.Map.entry("gpt-4o-2024-11-20", 1.25),
            java.util.Map.entry("gpt-4o-realtime-preview", 2.5),
            java.util.Map.entry("gpt-4o-mini-realtime-preview", 0.3),
            java.util.Map.entry("gpt-4.1", 1.0),
            java.util.Map.entry("gpt-4.1-mini", 0.2),
            java.util.Map.entry("gpt-4.1-nano", 0.05),
            java.util.Map.entry("gpt-image-1", 2.5),
            java.util.Map.entry("o1", 7.5),
            java.util.Map.entry("o1-preview", 7.5),
            java.util.Map.entry("o1-mini", 0.55),
            java.util.Map.entry("o1-pro", 75.0),
            java.util.Map.entry("o3-mini", 0.55),
            java.util.Map.entry("o3", 1.0),
            java.util.Map.entry("o3-pro", 10.0),
            java.util.Map.entry("o4-mini", 0.55),
            java.util.Map.entry("gpt-4o-mini", 0.075),
            java.util.Map.entry("gpt-4-turbo", 5.0),
            java.util.Map.entry("gpt-4.5-preview", 37.5),
            java.util.Map.entry("gpt-5", 0.625),
            java.util.Map.entry("gpt-5-mini", 0.125),
            java.util.Map.entry("gpt-5-nano", 0.025),
            java.util.Map.entry("gpt-3.5-turbo", 0.25),
            java.util.Map.entry("gpt-3.5-turbo-0613", 0.75),
            java.util.Map.entry("gpt-3.5-turbo-16k", 1.5),
            java.util.Map.entry("gpt-3.5-turbo-1106", 0.5),
            java.util.Map.entry("gpt-3.5-turbo-0125", 0.25),
            java.util.Map.entry("whisper-1", 15.0),
            java.util.Map.entry("tts-1", 7.5),
            java.util.Map.entry("tts-1-hd", 15.0),
            java.util.Map.entry("text-embedding-3-small", 0.01),
            java.util.Map.entry("text-embedding-3-large", 0.065),
            java.util.Map.entry("text-embedding-ada-002", 0.05),
            java.util.Map.entry("claude-3-haiku-20240307", 0.125),
            java.util.Map.entry("claude-3-5-haiku-20241022", 0.5),
            java.util.Map.entry("claude-3-sonnet-20240229", 1.5),
            java.util.Map.entry("claude-3-5-sonnet-20240620", 1.5),
            java.util.Map.entry("claude-3-7-sonnet-20250219", 1.5),
            java.util.Map.entry("claude-sonnet-4-20250514", 1.5),
            java.util.Map.entry("claude-opus-4-5-20251101", 2.5),
            java.util.Map.entry("claude-opus-4-6", 2.5),
            java.util.Map.entry("claude-opus-4-7", 2.5),
            java.util.Map.entry("claude-opus-4-8", 2.5),
            java.util.Map.entry("claude-3-opus-20240229", 7.5),
            java.util.Map.entry("claude-opus-4-20250514", 7.5),
            java.util.Map.entry("gemini-1.5-pro-latest", 1.25),
            java.util.Map.entry("gemini-1.5-flash-latest", 0.075),
            java.util.Map.entry("gemini-2.0-flash", 0.05),
            java.util.Map.entry("gemini-2.5-pro", 0.625),
            java.util.Map.entry("gemini-2.5-flash", 0.15),
            java.util.Map.entry("gemini-2.5-flash-preview-04-17", 0.075)
    );
}
