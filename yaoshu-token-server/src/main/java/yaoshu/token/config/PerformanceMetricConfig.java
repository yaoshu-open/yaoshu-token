package yaoshu.token.config;

import lombok.Data;

/**
 * 性能监控配置  */
public final class PerformanceMetricConfig {

    private PerformanceMetricConfig() {
    }

    @Data
    public static class PerfMetricsConfig {
        private boolean enabled;
        private int sampleRate = 100;
        private int retentionDays = 7;
    }

    @Data
    public static class PerformanceConfig2 {
        private boolean enabled;
        private int maxConcurrentRequests = 100;
        private int requestTimeoutSeconds = 300;
        private int connectionPoolSize = 50;
    }
}
