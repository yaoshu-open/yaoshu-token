package yaoshu.token.config.ratio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频输入倍率配置  * <p>
 * 启动期注入默认值，OptionService 在 {@code AudioRatio} key 写库后通过 update 全量覆盖。
 */
public final class AudioRatioConfig {

    private AudioRatioConfig() {
    }

    /** 默认音频倍率 */
    private static final Map<String, Double> DEFAULT_AUDIO_RATIO = Map.of(
            "gpt-4o-audio-preview", 16.0,
            "gpt-4o-mini-audio-preview", 66.67,
            "gpt-4o-realtime-preview", 8.0,
            "gpt-4o-mini-realtime-preview", 16.67,
            "gpt-4o-mini-tts", 25.0
    );

    private static final ConcurrentHashMap<String, Double> audioRatioMap = new ConcurrentHashMap<>(DEFAULT_AUDIO_RATIO);

    /**
     * 获取音频倍率，未命中返回 1.0。
     */
    public static double getAudioRatio(String modelName) {
        if (modelName == null) return 1.0;
        Double ratio = audioRatioMap.get(modelName);
        return ratio != null ? ratio : 1.0;
    }

    /** 检查模型是否存在显式音频倍率 */
    public static boolean contains(String modelName) {
        return modelName != null && audioRatioMap.containsKey(modelName);
    }

    public static Map<String, Double> getAudioRatioMap() {
        return new ConcurrentHashMap<>(audioRatioMap);
    }

    /**
     * 全量覆盖音频倍率。      * 采用 clear + putAll 语义。
     */
    public static void update(Map<String, Double> ratios) {
        audioRatioMap.clear();
        if (ratios != null && !ratios.isEmpty()) {
            audioRatioMap.putAll(ratios);
        }
    }
}
