package yaoshu.token.config.ratio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存倍率配置  */
public final class CacheRatioConfig {

    private CacheRatioConfig() {
    }

    /** 缓存读取倍率（cache_read_input_token 折扣） */
    private static final ConcurrentHashMap<String, Double> cacheRatioMap = new ConcurrentHashMap<>(Map.ofEntries(
            Map.entry("gemini-3-flash-preview", 0.1),
            Map.entry("gemini-3-pro-preview", 0.1),
            Map.entry("gemini-3.1-pro-preview", 0.1),
            Map.entry("gpt-4", 0.5),
            Map.entry("o1", 0.5),
            Map.entry("o1-mini", 0.5),
            Map.entry("o3-mini", 0.5),
            Map.entry("gpt-4o", 0.5),
            Map.entry("gpt-4o-mini", 0.5),
            Map.entry("gpt-4.1", 0.25),
            Map.entry("gpt-4.1-mini", 0.25),
            Map.entry("gpt-4.1-nano", 0.25),
            Map.entry("gpt-5", 0.1),
            Map.entry("gpt-5-mini", 0.1),
            Map.entry("gpt-5-nano", 0.1),
            Map.entry("deepseek-chat", 0.25),
            Map.entry("deepseek-reasoner", 0.25),
            Map.entry("deepseek-coder", 0.25),
            Map.entry("claude-3-sonnet-20240229", 0.1),
            Map.entry("claude-3-opus-20240229", 0.1),
            Map.entry("claude-3-haiku-20240307", 0.1),
            Map.entry("claude-3-5-sonnet-20241022", 0.1),
            Map.entry("claude-3-7-sonnet-20250219", 0.1),
            Map.entry("claude-sonnet-4-20250514", 0.1),
            Map.entry("claude-opus-4-20250514", 0.1)
    ));

    /** 缓存写入倍率（cache_creation_input_token 倍数） */
    private static final ConcurrentHashMap<String, Double> createCacheRatioMap = new ConcurrentHashMap<>(Map.ofEntries(
            Map.entry("claude-3-sonnet-20240229", 1.25),
            Map.entry("claude-3-opus-20240229", 1.25),
            Map.entry("claude-3-haiku-20240307", 1.25),
            Map.entry("claude-3-5-sonnet-20241022", 1.25),
            Map.entry("claude-3-7-sonnet-20250219", 1.25),
            Map.entry("claude-sonnet-4-20250514", 1.25),
            Map.entry("claude-opus-4-20250514", 1.25)
    ));

    public static Map<String, Double> getCacheRatioMap() {
        return new ConcurrentHashMap<>(cacheRatioMap);
    }

    public static Map<String, Double> getCreateCacheRatioMap() {
        return new ConcurrentHashMap<>(createCacheRatioMap);
    }

    /** 获取缓存倍率，默认返回 null */
    public static Double getCacheRatio(String modelName) {
        return cacheRatioMap.get(modelName);
    }

    public static Double getCreateCacheRatio(String modelName) {
        return createCacheRatioMap.get(modelName);
    }

    public static void updateCacheRatio(Map<String, Double> ratios) {
        cacheRatioMap.clear();
        if (ratios != null) cacheRatioMap.putAll(ratios);
    }

    /**
     * 单条更新缓存倍率（SPI 扩展点，供动态定价回写计费链路）。
     *
     * @param modelName 模型名
     * @param ratio     倍率值
     */
    public static void putCacheRatio(String modelName, Double ratio) {
        if (modelName != null && ratio != null) {
            cacheRatioMap.put(modelName, ratio);
        }
    }

    public static void updateCreateCacheRatio(Map<String, Double> ratios) {
        createCacheRatioMap.clear();
        if (ratios != null) createCacheRatioMap.putAll(ratios);
    }
}
