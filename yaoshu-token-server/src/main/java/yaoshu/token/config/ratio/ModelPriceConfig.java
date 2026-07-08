package yaoshu.token.config.ratio;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型价格配置（按次计费）  * 以及 GetModelPrice 函数。
 * <p>
 * 启动期注入默认值，OptionService 在 {@code ModelPrice} key 写库后通过 update 全量覆盖。
 */
public final class ModelPriceConfig {

    private ModelPriceConfig() {
    }

    /** 默认模型价格，与 Go defaultModelPrice 严格对齐 */
    private static final Map<String, Double> DEFAULT_MODEL_PRICE = buildDefaultModelPrice();

    private static final ConcurrentHashMap<String, Double> modelPriceMap = new ConcurrentHashMap<>(DEFAULT_MODEL_PRICE);

    private static Map<String, Double> buildDefaultModelPrice() {
        Map<String, Double> m = new HashMap<>();
        // Suno
        m.put("suno_music", 0.1);
        m.put("suno_lyrics", 0.01);
        // 图像生成
        m.put("dall-e-3", 0.04);
        m.put("imagen-3.0-generate-002", 0.03);
        m.put("black-forest-labs/flux-1.1-pro", 0.04);
        m.put("gpt-4-gizmo-*", 0.1);
        // Midjourney
        m.put("mj_video", 0.8);
        m.put("mj_imagine", 0.1);
        m.put("mj_edits", 0.1);
        m.put("mj_variation", 0.1);
        m.put("mj_reroll", 0.1);
        m.put("mj_blend", 0.1);
        m.put("mj_modal", 0.1);
        m.put("mj_zoom", 0.1);
        m.put("mj_shorten", 0.1);
        m.put("mj_high_variation", 0.1);
        m.put("mj_low_variation", 0.1);
        m.put("mj_pan", 0.1);
        m.put("mj_inpaint", 0.0);
        m.put("mj_custom_zoom", 0.0);
        m.put("mj_describe", 0.05);
        m.put("mj_upscale", 0.05);
        m.put("swap_face", 0.05);
        m.put("mj_upload", 0.05);
        // Sora
        m.put("sora-2", 0.3);
        m.put("sora-2-pro", 0.5);
        // GPT-4o-mini-tts（按次计费）
        m.put("gpt-4o-mini-tts", 0.3);
        // Veo（视频）
        m.put("veo-3.0-generate-001", 0.4);
        m.put("veo-3.0-fast-generate-001", 0.15);
        m.put("veo-3.1-generate-preview", 0.4);
        m.put("veo-3.1-fast-generate-preview", 0.15);
        return m;
    }

    /**
     * 获取模型价格。      * <p>
     * 流程：FormatMatchingModelName → 精确匹配 → 紧凑后缀通配兜底 → 未命中返回 -1。
     *
     * @param modelName 模型名
     * @return 价格；不存在返回 -1（PriceHelper 据此切换为按倍率计费）
     */
    public static double getModelPrice(String modelName) {
        if (modelName == null) return -1;
        String name = ModelRatioConfig.formatMatchingModelName(modelName);
        Double price = modelPriceMap.get(name);
        if (price != null) return price;
        // 紧凑模型后缀通配兜底
        if (name.endsWith(ModelRatioConfig.COMPACT_MODEL_SUFFIX)) {
            Double wildcard = modelPriceMap.get(ModelRatioConfig.COMPACT_WILDCARD_MODEL_KEY);
            if (wildcard != null) return wildcard;
        }
        return -1;
    }

    /** 检查模型是否配置了价格*/
    public static boolean contains(String modelName) {
        if (modelName == null) return false;
        return modelPriceMap.containsKey(ModelRatioConfig.formatMatchingModelName(modelName));
    }

    public static Map<String, Double> getModelPriceMap() {
        return new ConcurrentHashMap<>(modelPriceMap);
    }

    public static Map<String, Double> getDefaultModelPriceMap() {
        return new ConcurrentHashMap<>(DEFAULT_MODEL_PRICE);
    }

    /**
     * 全量覆盖模型价格。      * 采用 clear + putAll 语义。
     */
    public static void update(Map<String, Double> prices) {
        modelPriceMap.clear();
        if (prices != null && !prices.isEmpty()) {
            modelPriceMap.putAll(prices);
        }
    }
}
