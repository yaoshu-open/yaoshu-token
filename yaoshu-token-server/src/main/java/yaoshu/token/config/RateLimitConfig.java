package yaoshu.token.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 模型请求限流配置  */
public final class RateLimitConfig {

    private RateLimitConfig() {
    }

    /** 是否启用模型请求限流 */
    private static volatile boolean modelRequestRateLimitEnabled;

    /** 限流窗口（分钟） */
    private static volatile int modelRequestRateLimitDurationMinutes = 1;

    /** 限流总计数 */
    private static volatile int modelRequestRateLimitCount;

    /** 限流成功计数 */
    private static volatile int modelRequestRateLimitSuccessCount = 1000;

    /** 分组限流配置 [totalCount, successCount] */
    private static final ConcurrentHashMap<String, int[]> MODEL_REQUEST_RATE_LIMIT_GROUP = new ConcurrentHashMap<>();

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // ======================== Getters/Setters ========================

    public static boolean isModelRequestRateLimitEnabled() {
        return modelRequestRateLimitEnabled;
    }

    public static void setModelRequestRateLimitEnabled(boolean value) {
        modelRequestRateLimitEnabled = value;
    }

    public static int getModelRequestRateLimitDurationMinutes() {
        return modelRequestRateLimitDurationMinutes;
    }

    public static void setModelRequestRateLimitDurationMinutes(int value) {
        modelRequestRateLimitDurationMinutes = value;
    }

    public static int getModelRequestRateLimitCount() {
        return modelRequestRateLimitCount;
    }

    public static void setModelRequestRateLimitCount(int value) {
        modelRequestRateLimitCount = value;
    }

    public static int getModelRequestRateLimitSuccessCount() {
        return modelRequestRateLimitSuccessCount;
    }

    public static void setModelRequestRateLimitSuccessCount(int value) {
        modelRequestRateLimitSuccessCount = value;
    }

    // ======================== 分组限流 API ========================

    /** 获取分组的限流参数 [totalCount, successCount] */
    public static int[] getGroupRateLimit(String group) {
        int[] limits = MODEL_REQUEST_RATE_LIMIT_GROUP.get(group);
        return limits != null ? limits.clone() : null;
    }

    /** 更新分组限流配置 */
    public static void updateGroupRateLimit(Map<String, int[]> limits) {
        lock.writeLock().lock();
        try {
            MODEL_REQUEST_RATE_LIMIT_GROUP.clear();
            if (limits != null) MODEL_REQUEST_RATE_LIMIT_GROUP.putAll(limits);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 获取分组限流配置副本 */
    public static Map<String, int[]> getGroupRateLimitCopy() {
        return new ConcurrentHashMap<>(MODEL_REQUEST_RATE_LIMIT_GROUP);
    }
}
