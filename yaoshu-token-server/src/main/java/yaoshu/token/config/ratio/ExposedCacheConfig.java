package yaoshu.token.config.ratio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 暴露缓存配置  */
public final class ExposedCacheConfig {

    private ExposedCacheConfig() {
    }

    private static final ConcurrentHashMap<String, Double> exposedCacheMap = new ConcurrentHashMap<>();

    public static Map<String, Double> getExposedCacheMap() {
        return new ConcurrentHashMap<>(exposedCacheMap);
    }

    public static Double getExposedCache(String key) {
        return exposedCacheMap.get(key);
    }

    public static void update(Map<String, Double> ratios) {
        exposedCacheMap.clear();
        if (ratios != null) exposedCacheMap.putAll(ratios);
    }
}
