package yaoshu.token.config.ratio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片倍率配置  * <p>
 * 启动期注入默认值，OptionService 在 {@code ImageRatio} key 写库后通过 update 全量覆盖。
 */
public final class ImageRatioConfig {

    private ImageRatioConfig() {
    }

    /** 默认图片倍率 */
    private static final Map<String, Double> DEFAULT_IMAGE_RATIO = Map.of(
            "gpt-image-1", 2.0
    );

    private static final ConcurrentHashMap<String, Double> imageRatioMap = new ConcurrentHashMap<>(DEFAULT_IMAGE_RATIO);

    /**
     * 获取图片倍率，未命中返回 1.0。
     */
    public static double getImageRatio(String modelName) {
        if (modelName == null) return 1.0;
        Double ratio = imageRatioMap.get(modelName);
        return ratio != null ? ratio : 1.0;
    }

    /** 检查模型是否存在显式图片倍率 */
    public static boolean contains(String modelName) {
        return modelName != null && imageRatioMap.containsKey(modelName);
    }

    public static Map<String, Double> getImageRatioMap() {
        return new ConcurrentHashMap<>(imageRatioMap);
    }

    /**
     * 全量覆盖图片倍率。      * 采用 clear + putAll 语义，与 Go RWMap.LoadFromJsonString 行为一致。
     */
    public static void update(Map<String, Double> ratios) {
        imageRatioMap.clear();
        if (ratios != null && !ratios.isEmpty()) {
            imageRatioMap.putAll(ratios);
        }
    }
}
