package yaoshu.token.config;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 性能监控配置（线程安全）  */
public final class PerformanceConfig {

    private PerformanceConfig() {
    }

    /** 性能监控配置 */
    public record PerformanceMonitorConfig(boolean enabled, int cpuThreshold, int memoryThreshold, int diskThreshold) {
    }

    private static final AtomicReference<PerformanceMonitorConfig> configRef =
            new AtomicReference<>(new PerformanceMonitorConfig(true, 90, 90, 90));

    /** 获取当前性能监控配置 */
    public static PerformanceMonitorConfig getConfig() {
        return configRef.get();
    }

    /** 更新性能监控配置 */
    public static void setConfig(PerformanceMonitorConfig config) {
        configRef.set(config);
    }
}
