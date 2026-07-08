package yaoshu.token.config;

/**
 * 敏感词配置  */
public final class SensitiveConfig {

    /** 是否启用敏感词检测 */
    private static volatile boolean checkSensitiveEnabled = true;

    /** 是否启用 Prompt 敏感词检测 */
    private static volatile boolean checkSensitiveOnPromptEnabled = true;

    /** 检测到敏感词是否停止生成（否则替换） */
    private static volatile boolean stopOnSensitiveEnabled = true;

    /** 流模式缓存队列长度，0 表示无缓存 */
    private static volatile int streamCacheQueueLength;

    /** 敏感词列表（线程安全） */
    private static final java.util.concurrent.CopyOnWriteArrayList<String> SENSITIVE_WORDS =
            new java.util.concurrent.CopyOnWriteArrayList<>(java.util.List.of("test_sensitive"));

    private SensitiveConfig() {
    }

    public static boolean isCheckSensitiveEnabled() {
        return checkSensitiveEnabled;
    }

    public static void setCheckSensitiveEnabled(boolean value) {
        checkSensitiveEnabled = value;
    }

    public static boolean isCheckSensitiveOnPromptEnabled() {
        return checkSensitiveOnPromptEnabled;
    }

    public static void setCheckSensitiveOnPromptEnabled(boolean value) {
        checkSensitiveOnPromptEnabled = value;
    }

    public static boolean isStopOnSensitiveEnabled() {
        return stopOnSensitiveEnabled;
    }

    public static void setStopOnSensitiveEnabled(boolean value) {
        stopOnSensitiveEnabled = value;
    }

    public static int getStreamCacheQueueLength() {
        return streamCacheQueueLength;
    }

    public static void setStreamCacheQueueLength(int value) {
        streamCacheQueueLength = value;
    }

    /** 获取敏感词列表（防御性拷贝） */
    public static java.util.List<String> getSensitiveWords() {
        return java.util.List.copyOf(SENSITIVE_WORDS);
    }

    /** 从换行分隔的字符串同步敏感词 */
    public static void updateFromString(String input) {
        SENSITIVE_WORDS.clear();
        if (input != null && !input.isEmpty()) {
            for (String w : input.split("\n")) {
                w = w.trim();
                if (!w.isEmpty()) SENSITIVE_WORDS.add(w);
            }
        }
    }

    /** 是否应检查 Prompt 敏感词 */
    public static boolean shouldCheckPromptSensitive() {
        return checkSensitiveEnabled && checkSensitiveOnPromptEnabled;
    }
}
