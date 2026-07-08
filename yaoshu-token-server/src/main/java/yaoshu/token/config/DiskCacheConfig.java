package yaoshu.token.config;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 磁盘缓存配置与统计  */
public final class DiskCacheConfig {

    private DiskCacheConfig() {
    }

    /** 磁盘缓存配置 */
    public record Config(boolean enabled, int thresholdMB, int maxSizeMB, String path) {
    }

    /** 磁盘缓存统计 */
    public static class Stats {
        public volatile long activeDiskFiles;
        public volatile long currentDiskUsageBytes;
        public volatile long activeMemoryBuffers;
        public volatile long currentMemoryUsageBytes;
        public volatile long diskCacheHits;
        public volatile long memoryCacheHits;
        public volatile long diskCacheMaxBytes;
        public volatile long diskCacheThresholdBytes;
    }

    private static volatile Config currentConfig = new Config(false, 10, 1024, "");
    private static final ReentrantReadWriteLock configLock = new ReentrantReadWriteLock();
    private static final Stats stats = new Stats();

    // ======================== 配置方法 ========================

    public static Config getConfig() {
        configLock.readLock().lock();
        try { return currentConfig; } finally { configLock.readLock().unlock(); }
    }

    public static void setConfig(Config config) {
        configLock.writeLock().lock();
        try { currentConfig = config; } finally { configLock.writeLock().unlock(); }
    }

    public static boolean isEnabled() {
        configLock.readLock().lock();
        try { return currentConfig.enabled(); } finally { configLock.readLock().unlock(); }
    }

    /** 获取磁盘缓存阈值（字节） */
    public static long getThresholdBytes() {
        configLock.readLock().lock();
        try { return (long) currentConfig.thresholdMB() << 20; } finally { configLock.readLock().unlock(); }
    }

    /** 获取磁盘缓存最大大小（字节） */
    public static long getMaxSizeBytes() {
        configLock.readLock().lock();
        try { return (long) currentConfig.maxSizeMB() << 20; } finally { configLock.readLock().unlock(); }
    }

    /** 获取磁盘缓存目录 */
    public static String getPath() {
        configLock.readLock().lock();
        try { return currentConfig.path(); } finally { configLock.readLock().unlock(); }
    }

    // ======================== 统计方法 ========================

    public static Stats getStats() {
        Stats s = new Stats();
        s.activeDiskFiles = stats.activeDiskFiles;
        s.currentDiskUsageBytes = stats.currentDiskUsageBytes;
        s.activeMemoryBuffers = stats.activeMemoryBuffers;
        s.currentMemoryUsageBytes = stats.currentMemoryUsageBytes;
        s.diskCacheHits = stats.diskCacheHits;
        s.memoryCacheHits = stats.memoryCacheHits;
        s.diskCacheMaxBytes = getMaxSizeBytes();
        s.diskCacheThresholdBytes = getThresholdBytes();
        return s;
    }

    /** 检查是否可以创建新的磁盘缓存 */
    public static boolean isAvailable(long requestSize) {
        if (!isEnabled()) return false;
        long maxBytes = getMaxSizeBytes();
        return stats.currentDiskUsageBytes + requestSize <= maxBytes;
    }

    // ======================== 原子统计操作 ========================

    public static void incrementDiskFiles(long size) {
        synchronized (stats) {
            stats.activeDiskFiles++;
            stats.currentDiskUsageBytes += size;
        }
    }

    public static void decrementDiskFiles(long size) {
        synchronized (stats) {
            if (stats.activeDiskFiles > 0) stats.activeDiskFiles--;
            stats.currentDiskUsageBytes = Math.max(0, stats.currentDiskUsageBytes - size);
        }
    }

    public static void incrementMemoryBuffers(long size) {
        synchronized (stats) {
            stats.activeMemoryBuffers++;
            stats.currentMemoryUsageBytes += size;
        }
    }

    public static void decrementMemoryBuffers(long size) {
        synchronized (stats) {
            if (stats.activeMemoryBuffers > 0) stats.activeMemoryBuffers--;
            stats.currentMemoryUsageBytes = Math.max(0, stats.currentMemoryUsageBytes - size);
        }
    }

    public static void incrementDiskCacheHits() { stats.diskCacheHits++; }

    public static void incrementMemoryCacheHits() { stats.memoryCacheHits++; }

    public static void resetDiskCacheStats() {
        synchronized (stats) {
            stats.diskCacheHits = 0;
            stats.memoryCacheHits = 0;
        }
    }

    public static void resetDiskCacheUsage() {
        synchronized (stats) {
            stats.activeDiskFiles = 0;
            stats.currentDiskUsageBytes = 0;
        }
    }
}
