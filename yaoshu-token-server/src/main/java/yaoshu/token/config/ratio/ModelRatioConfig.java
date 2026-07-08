package yaoshu.token.config.ratio;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型倍率配置  * 以及 GetModelRatio / FormatMatchingModelName 函数。
 * <p>
 * 启动期注入默认值，OptionService 在 {@code ModelRatio} key 写库后通过 update 全量覆盖。
 * 默认条目数量与 Go defaultModelRatio 严格对齐（~150 条）。
 */
public final class ModelRatioConfig {

    private ModelRatioConfig() {
    }

    /** 1 USD = 7.3 RMB（与 Go ratio_setting.USD2RMB 一致） */
    private static final double USD2RMB = 7.3;
    /** $0.002 = 1 → $1 = 500 */
    private static final double USD = 500.0;
    /** RMB = USD / USD2RMB */
    private static final double RMB = USD / USD2RMB;

    /** 紧凑模型后缀 */
    public static final String COMPACT_MODEL_SUFFIX = "-openai-compact";
    /** 紧凑模型通配 key */
    public static final String COMPACT_WILDCARD_MODEL_KEY = "*" + COMPACT_MODEL_SUFFIX;

    /** 默认模型倍率，与 Go defaultModelRatio 严格对齐 */
    private static final Map<String, Double> DEFAULT_MODEL_RATIO = buildDefaultModelRatio();

    private static final ConcurrentHashMap<String, Double> modelRatioMap = new ConcurrentHashMap<>(DEFAULT_MODEL_RATIO);

    private static Map<String, Double> buildDefaultModelRatio() {
        Map<String, Double> m = new HashMap<>();
        // GPT-4 系列
        m.put("gpt-4-gizmo-*", 15.0);
        m.put("gpt-4o-gizmo-*", 2.5);
        m.put("gpt-4-all", 15.0);
        m.put("gpt-4o-all", 15.0);
        m.put("gpt-4", 15.0);
        m.put("gpt-4-0613", 15.0);
        m.put("gpt-4-32k", 30.0);
        m.put("gpt-4-32k-0613", 30.0);
        m.put("gpt-4-1106-preview", 5.0);
        m.put("gpt-4-0125-preview", 5.0);
        m.put("gpt-4-turbo-preview", 5.0);
        m.put("gpt-4-vision-preview", 5.0);
        m.put("gpt-4-1106-vision-preview", 5.0);
        // GPT-4o 系列
        m.put("chatgpt-4o-latest", 2.5);
        m.put("gpt-4o", 1.25);
        m.put("gpt-4o-audio-preview", 1.25);
        m.put("gpt-4o-audio-preview-2024-10-01", 1.25);
        m.put("gpt-4o-2024-05-13", 2.5);
        m.put("gpt-4o-2024-08-06", 1.25);
        m.put("gpt-4o-2024-11-20", 1.25);
        m.put("gpt-4o-realtime-preview", 2.5);
        m.put("gpt-4o-realtime-preview-2024-10-01", 2.5);
        m.put("gpt-4o-realtime-preview-2024-12-17", 2.5);
        m.put("gpt-4o-mini-realtime-preview", 0.3);
        m.put("gpt-4o-mini-realtime-preview-2024-12-17", 0.3);
        // GPT-4.1
        m.put("gpt-4.1", 1.0);
        m.put("gpt-4.1-2025-04-14", 1.0);
        m.put("gpt-4.1-mini", 0.2);
        m.put("gpt-4.1-mini-2025-04-14", 0.2);
        m.put("gpt-4.1-nano", 0.05);
        m.put("gpt-4.1-nano-2025-04-14", 0.05);
        m.put("gpt-image-1", 2.5);
        // o1 系列
        m.put("o1", 7.5);
        m.put("o1-2024-12-17", 7.5);
        m.put("o1-preview", 7.5);
        m.put("o1-preview-2024-09-12", 7.5);
        m.put("o1-mini", 0.55);
        m.put("o1-mini-2024-09-12", 0.55);
        m.put("o1-pro", 75.0);
        m.put("o1-pro-2025-03-19", 75.0);
        // o3 系列
        m.put("o3-mini", 0.55);
        m.put("o3-mini-2025-01-31", 0.55);
        m.put("o3-mini-high", 0.55);
        m.put("o3-mini-2025-01-31-high", 0.55);
        m.put("o3-mini-low", 0.55);
        m.put("o3-mini-2025-01-31-low", 0.55);
        m.put("o3-mini-medium", 0.55);
        m.put("o3-mini-2025-01-31-medium", 0.55);
        m.put("o3", 1.0);
        m.put("o3-2025-04-16", 1.0);
        m.put("o3-pro", 10.0);
        m.put("o3-pro-2025-06-10", 10.0);
        m.put("o3-deep-research", 5.0);
        m.put("o3-deep-research-2025-06-26", 5.0);
        // o4 系列
        m.put("o4-mini", 0.55);
        m.put("o4-mini-2025-04-16", 0.55);
        m.put("o4-mini-deep-research", 1.0);
        m.put("o4-mini-deep-research-2025-06-26", 1.0);
        // GPT-4o-mini
        m.put("gpt-4o-mini", 0.075);
        m.put("gpt-4o-mini-2024-07-18", 0.075);
        // GPT-4-turbo
        m.put("gpt-4-turbo", 5.0);
        m.put("gpt-4-turbo-2024-04-09", 5.0);
        // GPT-4.5
        m.put("gpt-4.5-preview", 37.5);
        m.put("gpt-4.5-preview-2025-02-27", 37.5);
        // GPT-5
        m.put("gpt-5", 0.625);
        m.put("gpt-5-2025-08-07", 0.625);
        m.put("gpt-5-chat-latest", 0.625);
        m.put("gpt-5-mini", 0.125);
        m.put("gpt-5-mini-2025-08-07", 0.125);
        m.put("gpt-5-nano", 0.025);
        m.put("gpt-5-nano-2025-08-07", 0.025);
        // GPT-3.5 系列
        m.put("gpt-3.5-turbo", 0.25);
        m.put("gpt-3.5-turbo-16k", 1.5);
        m.put("gpt-3.5-turbo-instruct", 0.75);
        m.put("gpt-3.5-turbo-1106", 0.5);
        m.put("gpt-3.5-turbo-0125", 0.25);
        // 旧模型
        m.put("babbage-002", 0.2);
        m.put("davinci-002", 1.0);
        // Whisper / TTS
        m.put("whisper-1", 15.0);
        m.put("tts-1", 7.5);
        m.put("tts-1-1106", 7.5);
        m.put("tts-1-hd", 15.0);
        m.put("tts-1-hd-1106", 15.0);
        // Embedding
        m.put("text-embedding-3-small", 0.01);
        m.put("text-embedding-3-large", 0.065);
        m.put("text-embedding-ada-002", 0.05);
        m.put("text-moderation-stable", 0.1);
        m.put("text-moderation-latest", 0.1);
        // Claude 系列
        m.put("claude-3-haiku-20240307", 0.125);
        m.put("claude-3-5-haiku-20241022", 0.5);
        m.put("claude-haiku-4-5-20251001", 0.5);
        m.put("claude-3-sonnet-20240229", 1.5);
        m.put("claude-3-5-sonnet-20240620", 1.5);
        m.put("claude-3-5-sonnet-20241022", 1.5);
        m.put("claude-3-7-sonnet-20250219", 1.5);
        m.put("claude-3-7-sonnet-20250219-thinking", 1.5);
        m.put("claude-sonnet-4-20250514", 1.5);
        m.put("claude-sonnet-4-5-20250929", 1.5);
        m.put("claude-opus-4-5-20251101", 2.5);
        m.put("claude-opus-4-6", 2.5);
        m.put("claude-opus-4-6-max", 2.5);
        m.put("claude-opus-4-6-high", 2.5);
        m.put("claude-opus-4-6-medium", 2.5);
        m.put("claude-opus-4-6-low", 2.5);
        m.put("claude-opus-4-7", 2.5);
        m.put("claude-opus-4-7-max", 2.5);
        m.put("claude-opus-4-7-xhigh", 2.5);
        m.put("claude-opus-4-7-high", 2.5);
        m.put("claude-opus-4-7-medium", 2.5);
        m.put("claude-opus-4-7-low", 2.5);
        m.put("claude-opus-4-8", 2.5);
        m.put("claude-opus-4-8-max", 2.5);
        m.put("claude-opus-4-8-xhigh", 2.5);
        m.put("claude-opus-4-8-high", 2.5);
        m.put("claude-opus-4-8-medium", 2.5);
        m.put("claude-opus-4-8-low", 2.5);
        m.put("claude-3-opus-20240229", 7.5);
        m.put("claude-opus-4-20250514", 7.5);
        m.put("claude-opus-4-1-20250805", 7.5);
        // 文心 ERNIE（按 RMB 折算）
        m.put("ERNIE-4.0-8K", 0.120 * RMB);
        m.put("ERNIE-3.5-8K", 0.012 * RMB);
        m.put("ERNIE-3.5-8K-0205", 0.024 * RMB);
        m.put("ERNIE-3.5-8K-1222", 0.012 * RMB);
        m.put("ERNIE-Bot-8K", 0.024 * RMB);
        m.put("ERNIE-3.5-4K-0205", 0.012 * RMB);
        m.put("ERNIE-Speed-8K", 0.004 * RMB);
        m.put("ERNIE-Speed-128K", 0.004 * RMB);
        m.put("ERNIE-Lite-8K-0922", 0.008 * RMB);
        m.put("ERNIE-Lite-8K-0308", 0.003 * RMB);
        m.put("ERNIE-Tiny-8K", 0.001 * RMB);
        m.put("BLOOMZ-7B", 0.004 * RMB);
        m.put("Embedding-V1", 0.002 * RMB);
        m.put("bge-large-zh", 0.002 * RMB);
        m.put("bge-large-en", 0.002 * RMB);
        m.put("tao-8k", 0.002 * RMB);
        // PaLM / Gemini
        m.put("gemini-1.5-pro-latest", 1.25);
        m.put("gemini-1.5-flash-latest", 0.075);
        m.put("gemini-2.0-flash", 0.05);
        m.put("gemini-2.5-pro-exp-03-25", 0.625);
        m.put("gemini-2.5-pro-preview-03-25", 0.625);
        m.put("gemini-2.5-pro", 0.625);
        m.put("gemini-2.5-flash-preview-04-17", 0.075);
        m.put("gemini-2.5-flash-preview-04-17-thinking", 0.075);
        m.put("gemini-2.5-flash-preview-04-17-nothinking", 0.075);
        m.put("gemini-2.5-flash-preview-05-20", 0.075);
        m.put("gemini-2.5-flash-preview-05-20-thinking", 0.075);
        m.put("gemini-2.5-flash-preview-05-20-nothinking", 0.075);
        m.put("gemini-2.5-flash-thinking-*", 0.075);
        m.put("gemini-2.5-pro-thinking-*", 0.625);
        m.put("gemini-2.5-flash-lite-preview-thinking-*", 0.05);
        m.put("gemini-2.5-flash-lite-preview-06-17", 0.05);
        m.put("gemini-2.5-flash", 0.15);
        m.put("gemini-robotics-er-1.5-preview", 0.15);
        m.put("gemini-embedding-001", 0.075);
        m.put("text-embedding-004", 0.001);
        // 智谱 GLM
        m.put("chatglm_turbo", 0.3572);
        m.put("chatglm_pro", 0.7143);
        m.put("chatglm_std", 0.3572);
        m.put("chatglm_lite", 0.1429);
        m.put("glm-4", 7.143);
        m.put("glm-4v", 0.05 * RMB);
        m.put("glm-4-alltools", 0.1 * RMB);
        m.put("glm-3-turbo", 0.3572);
        m.put("glm-4-plus", 0.05 * RMB);
        m.put("glm-4-0520", 0.1 * RMB);
        m.put("glm-4-air", 0.001 * RMB);
        m.put("glm-4-airx", 0.01 * RMB);
        m.put("glm-4-long", 0.001 * RMB);
        m.put("glm-4-flash", 0.0);
        m.put("glm-4v-plus", 0.01 * RMB);
        // 通义千问
        m.put("qwen-turbo", 0.8572);
        m.put("qwen-plus", 10.0);
        m.put("text-embedding-v1", 0.05);
        // 讯飞星火
        m.put("SparkDesk-v1.1", 1.2858);
        m.put("SparkDesk-v2.1", 1.2858);
        m.put("SparkDesk-v3.1", 1.2858);
        m.put("SparkDesk-v3.5", 1.2858);
        m.put("SparkDesk-v4.0", 1.2858);
        // 360 GPT
        m.put("360GPT_S2_V9", 0.8572);
        m.put("360gpt-turbo", 0.0858);
        m.put("360gpt-turbo-responsibility-8k", 0.8572);
        m.put("360gpt-pro", 0.8572);
        m.put("360gpt2-pro", 0.8572);
        m.put("embedding-bert-512-v1", 0.0715);
        m.put("embedding_s1_v1", 0.0715);
        m.put("semantic_similarity_s1_v1", 0.0715);
        // 腾讯混元
        m.put("hunyuan", 7.143);
        // 零一万物（按 7.2 RMB 折算 USD）
        m.put("yi-34b-chat-0205", 0.18);
        m.put("yi-34b-chat-200k", 0.864);
        m.put("yi-vl-plus", 0.432);
        m.put("yi-large", 20.0 / 1000 * RMB);
        m.put("yi-medium", 2.5 / 1000 * RMB);
        m.put("yi-vision", 6.0 / 1000 * RMB);
        m.put("yi-medium-200k", 12.0 / 1000 * RMB);
        m.put("yi-spark", 1.0 / 1000 * RMB);
        m.put("yi-large-rag", 25.0 / 1000 * RMB);
        m.put("yi-large-turbo", 12.0 / 1000 * RMB);
        m.put("yi-large-preview", 20.0 / 1000 * RMB);
        m.put("yi-large-rag-preview", 25.0 / 1000 * RMB);
        // Cohere Command
        m.put("command", 0.5);
        m.put("command-nightly", 0.5);
        m.put("command-light", 0.5);
        m.put("command-light-nightly", 0.5);
        m.put("command-r", 0.25);
        m.put("command-r-plus", 1.5);
        m.put("command-r-08-2024", 0.075);
        m.put("command-r-plus-08-2024", 1.25);
        // DeepSeek
        m.put("deepseek-chat", 0.27 / 2);
        m.put("deepseek-coder", 0.27 / 2);
        m.put("deepseek-reasoner", 0.55 / 2);
        // Perplexity（不计入搜索费用）
        m.put("llama-3-sonar-small-32k-chat", 0.2 / 1000 * USD);
        m.put("llama-3-sonar-small-32k-online", 0.2 / 1000 * USD);
        m.put("llama-3-sonar-large-32k-chat", 1.0 / 1000 * USD);
        m.put("llama-3-sonar-large-32k-online", 1.0 / 1000 * USD);
        // Grok
        m.put("grok-3-beta", 1.5);
        m.put("grok-3-mini-beta", 0.15);
        m.put("grok-2", 1.0);
        m.put("grok-2-vision", 1.0);
        m.put("grok-beta", 2.5);
        m.put("grok-vision-beta", 2.5);
        m.put("grok-3-fast-beta", 2.5);
        m.put("grok-3-mini-fast-beta", 0.3);
        // submodel
        m.put("NousResearch/Hermes-4-405B-FP8", 0.8);
        m.put("Qwen/Qwen3-235B-A22B-Thinking-2507", 0.6);
        m.put("Qwen/Qwen3-Coder-480B-A35B-Instruct-FP8", 0.8);
        m.put("Qwen/Qwen3-235B-A22B-Instruct-2507", 0.3);
        m.put("zai-org/GLM-4.5-FP8", 0.8);
        m.put("openai/gpt-oss-120b", 0.5);
        m.put("deepseek-ai/DeepSeek-R1-0528", 0.8);
        m.put("deepseek-ai/DeepSeek-R1", 0.8);
        m.put("deepseek-ai/DeepSeek-V3-0324", 0.8);
        m.put("deepseek-ai/DeepSeek-V3.1", 0.8);
        return m;
    }

    /**
     * 获取模型倍率。      * <p>
     * 流程：FormatMatchingModelName → 精确匹配 → 通配符兜底（紧凑后缀） → 默认 37.5（Go selfUseMode 兜底）。
     *
     * @param modelName  模型名（调用方未做归一化，由本方法内部 format）
     * @return 倍率值；未配置时返回 37.5（Go SelfUseModeEnabled 兜底语义）
     */
    public static double getModelRatio(String modelName) {
        if (modelName == null) return 37.5;
        String name = formatMatchingModelName(modelName);
        Double ratio = modelRatioMap.get(name);
        if (ratio != null) return ratio;
        // 紧凑模型后缀通配兜底
        if (name.endsWith(COMPACT_MODEL_SUFFIX)) {
            Double wildcard = modelRatioMap.get(COMPACT_WILDCARD_MODEL_KEY);
            if (wildcard != null) return wildcard;
        }
        return 37.5;
    }

    /**
     * 获取模型倍率，未配置返回 null（fail-fast 版本）。
     * <p>
     * 与 {@link #getModelRatio(String)} 的区别：未配置时返回 null 而非 37.5 兜底，
     * 供计费扣费点使用——未配置模型应直接拒绝请求，避免按 37.5 倍率（$75/M）静默多扣费。
     * <p>
     * 流程与 getModelRatio 完全一致（精确匹配 → 通配符兜底），仅末尾返回值不同。
     *
     * @param modelName 模型名（调用方未做归一化，由本方法内部 format）
     * @return 倍率值；未配置返回 null
     */
    public static Double getModelRatioOrNull(String modelName) {
        if (modelName == null) return null;
        String name = formatMatchingModelName(modelName);
        Double ratio = modelRatioMap.get(name);
        if (ratio != null) return ratio;
        // 紧凑模型后缀通配兜底
        if (name.endsWith(COMPACT_MODEL_SUFFIX)) {
            Double wildcard = modelRatioMap.get(COMPACT_WILDCARD_MODEL_KEY);
            if (wildcard != null) return wildcard;
        }
        return null;
    }

    /** 检查模型是否配置了倍率*/
    public static boolean contains(String modelName) {
        if (modelName == null) return false;
        return modelRatioMap.containsKey(formatMatchingModelName(modelName));
    }

    public static Map<String, Double> getModelRatioMap() {
        return new ConcurrentHashMap<>(modelRatioMap);
    }

    public static Map<String, Double> getDefaultModelRatioMap() {
        return new ConcurrentHashMap<>(DEFAULT_MODEL_RATIO);
    }

    /**
     * 全量覆盖模型倍率。      * 采用 clear + putAll 语义。
     */
    public static void update(Map<String, Double> ratios) {
        modelRatioMap.clear();
        if (ratios != null && !ratios.isEmpty()) {
            modelRatioMap.putAll(ratios);
        }
    }

    /**
     * 单条更新模型倍率（SPI 扩展点，供动态定价回写计费链路）。
     * <p>
     * 商业版 EePricingEnhancer 计算出动态倍率后，通过此方法回写到计费内存 Map，
     * 使实际扣费使用动态定价倍率而非静态默认值。
     *
     * @param modelName 模型名（内部自动归一化）
     * @param ratio     倍率值
     */
    public static void putModelRatio(String modelName, double ratio) {
        if (modelName != null) {
            modelRatioMap.put(formatMatchingModelName(modelName), ratio);
        }
    }

    /**
     * 移除模型倍率条目（主要用于测试清理，避免全局静态状态污染）。
     *
     * @param modelName 模型名（内部自动归一化）
     */
    public static void removeModelRatio(String modelName) {
        if (modelName != null) {
            modelRatioMap.remove(formatMatchingModelName(modelName));
        }
    }

    /**
     * 模型名归一化。      * <p>
     * 处理：
     * 1. gemini-2.5-flash-lite/flash/pro 思考预算模型 → 折叠为 thinking-* 通配
     * 2. gpt-4-gizmo* / gpt-4o-gizmo* → 折叠为 gizmo-* 通配
     */
    public static String formatMatchingModelName(String name) {
        if (name == null) return null;
        if (name.startsWith("gemini-2.5-flash-lite")) {
            name = handleThinkingBudgetModel(name, "gemini-2.5-flash-lite", "gemini-2.5-flash-lite-thinking-*");
        } else if (name.startsWith("gemini-2.5-flash")) {
            name = handleThinkingBudgetModel(name, "gemini-2.5-flash", "gemini-2.5-flash-thinking-*");
        } else if (name.startsWith("gemini-2.5-pro")) {
            name = handleThinkingBudgetModel(name, "gemini-2.5-pro", "gemini-2.5-pro-thinking-*");
        }
        if (name.startsWith("gpt-4-gizmo")) {
            name = "gpt-4-gizmo-*";
        }
        if (name.startsWith("gpt-4o-gizmo")) {
            name = "gpt-4o-gizmo-*";
        }
        return name;
    }

    /**
     * 思考预算模型名折叠。      * 当 name 以 prefix 起首且包含 "-thinking-" 时返回通配名。
     */
    private static String handleThinkingBudgetModel(String name, String prefix, String wildcard) {
        if (name.startsWith(prefix) && name.contains("-thinking-")) {
            return wildcard;
        }
        return name;
    }
}
