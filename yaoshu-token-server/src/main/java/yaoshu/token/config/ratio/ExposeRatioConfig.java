package yaoshu.token.config.ratio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 暴露比例配置  * <p>
 * 暴露数据（非普通 token 计费）的比率映射。
 */
public final class ExposeRatioConfig {

    private ExposeRatioConfig() {
    }

    private static final ConcurrentHashMap<String, Double> exposeRatioMap = new ConcurrentHashMap<>();

    static {
        // 启动时注入图像模型默认倍率，OptionService 后续 update 可覆盖
        exposeRatioMap.put("image:gpt-image-1", 2.0);
    }

    public static Map<String, Double> getExposeRatioMap() {
        return new ConcurrentHashMap<>(exposeRatioMap);
    }

    public static Double getExposeRatio(String key) {
        return exposeRatioMap.get(key);
    }

    public static void update(Map<String, Double> ratios) {
        exposeRatioMap.clear();
        if (ratios != null) exposeRatioMap.putAll(ratios);
    }
}
