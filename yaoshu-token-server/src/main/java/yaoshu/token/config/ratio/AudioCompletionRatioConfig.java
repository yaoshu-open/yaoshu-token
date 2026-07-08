package yaoshu.token.config.ratio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频补全倍率配置  * <p>
 * 启动期注入默认值，OptionService 在 {@code AudioCompletionRatio} key 写库后通过 update 全量覆盖。
 */
public final class AudioCompletionRatioConfig {

    private AudioCompletionRatioConfig() {
    }

    /** 默认音频补全倍率 */
    private static final Map<String, Double> DEFAULT_AUDIO_COMPLETION_RATIO = Map.of(
            "gpt-4o-realtime", 2.0,
            "gpt-4o-mini-realtime", 2.0,
            "gpt-4o-mini-tts", 1.0,
            "tts-1", 0.0,
            "tts-1-hd", 0.0,
            "tts-1-1106", 0.0,
            "tts-1-hd-1106", 0.0
    );

    private static final ConcurrentHashMap<String, Double> audioCompletionRatioMap = new ConcurrentHashMap<>(DEFAULT_AUDIO_COMPLETION_RATIO);

    /**
     * 获取音频补全倍率，未命中返回 1.0。
     */
    public static double getAudioCompletionRatio(String modelName) {
        if (modelName == null) return 1.0;
        Double ratio = audioCompletionRatioMap.get(modelName);
        return ratio != null ? ratio : 1.0;
    }

    /** 检查模型是否存在显式音频补全倍率 */
    public static boolean contains(String modelName) {
        return modelName != null && audioCompletionRatioMap.containsKey(modelName);
    }

    public static Map<String, Double> getAudioCompletionRatioMap() {
        return new ConcurrentHashMap<>(audioCompletionRatioMap);
    }

    /**
     * 全量覆盖音频补全倍率。      * 采用 clear + putAll 语义。
     */
    public static void update(Map<String, Double> ratios) {
        audioCompletionRatioMap.clear();
        if (ratios != null && !ratios.isEmpty()) {
            audioCompletionRatioMap.putAll(ratios);
        }
    }
}
