package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.DiskCacheConfig;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 磁盘缓存操作服务  */
@Slf4j
public final class DiskCacheService {

    private DiskCacheService() {
    }

    /** 统一的缓存目录名 */
    private static final String CACHE_DIR = "yaoshu-token-body-cache";

    /** 获取磁盘缓存目录 */
    public static String getCacheDir() {
        String cachePath = DiskCacheConfig.getPath();
        if (cachePath == null || cachePath.isBlank()) {
            cachePath = System.getProperty("java.io.tmpdir");
        }
        return cachePath + "/" + CACHE_DIR;
    }

    /** 确保缓存目录存在 */
    public static void ensureCacheDir() throws IOException {
        Files.createDirectories(Path.of(getCacheDir()));
    }

    /** 创建磁盘缓存文件（返回路径 + 文件句柄） */
    public record CacheFile(String filePath, RandomAccessFile file) implements AutoCloseable {
        @Override
        public void close() throws Exception { file.close(); }
    }

    /** 创建磁盘缓存文件 */
    public static CacheFile createCacheFile(String cacheType) throws IOException {
        ensureCacheDir();
        String dir = getCacheDir();
        String filename = cacheType + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + "-" + System.nanoTime() + ".tmp";
        Path filePath = Path.of(dir, filename);
        RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw");
        return new CacheFile(filePath.toString(), file);
    }

    /** 写入数据到磁盘缓存文件，返回文件路径 */
    public static String writeCacheFile(String cacheType, byte[] data) throws IOException {
        CacheFile cache = createCacheFile(cacheType);
        try {
            cache.file().write(data);
            return cache.filePath();
        } catch (IOException e) {
            try { Files.deleteIfExists(Path.of(cache.filePath())); } catch (IOException ex) { log.warn("删除缓存文件失败: {}", cache.filePath(), ex); }
            throw e;
        } finally {
            try { cache.file().close(); } catch (IOException ex) { log.warn("关闭缓存文件失败: {}", cache.filePath(), ex); }
        }
    }

    /** 读取磁盘缓存文件 */
    public static byte[] readCacheFile(String filePath) throws IOException {
        return Files.readAllBytes(Path.of(filePath));
    }

    /** 读取磁盘缓存文件为字符串 */
    public static String readCacheFileString(String filePath) throws IOException {
        return Files.readString(Path.of(filePath));
    }

    /** 删除磁盘缓存文件 */
    public static void removeCacheFile(String filePath) throws IOException {
        Files.deleteIfExists(Path.of(filePath));
    }

    /** 清理旧的缓存文件 */
    public static void cleanupOldCacheFiles(Duration maxAge) {
        Path dir = Path.of(getCacheDir());
        if (!Files.exists(dir)) return;

        Instant cutoff = Instant.now().minus(maxAge);
        try {
            Files.list(dir).forEach(file -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        long size = attrs.size();
                        Files.deleteIfExists(file);
                        DiskCacheConfig.decrementDiskFiles(size);
                    }
                } catch (IOException ex) {
                    log.warn("清理缓存文件失败: {}", file, ex);
                }
            });
        } catch (IOException e) {
            log.debug("列出缓存目录失败: {}", dir, e);
        }
    }

    /** 获取磁盘缓存目录信息 */
    public record CacheInfo(int fileCount, long totalSize) {
    }

    public static CacheInfo getCacheInfo() {
        Path dir = Path.of(getCacheDir());
        if (!Files.exists(dir)) return new CacheInfo(0, 0);

        AtomicInteger count = new AtomicInteger();
        AtomicLong total = new AtomicLong();
        try {
            Files.list(dir).forEach(file -> {
                try {
                    if (Files.isRegularFile(file)) {
                        count.incrementAndGet();
                        total.addAndGet(Files.size(file));
                    }
                } catch (IOException ex) {
                    log.debug("读取缓存文件大小失败: {}", file, ex);
                }
            });
        } catch (IOException e) {
            log.debug("列出缓存目录失败: {}", dir, e);
        }
        return new CacheInfo(count.get(), total.get());
    }

    /** 判断是否应该使用磁盘缓存 */
    public static boolean shouldUseDiskCache(long dataSize) {
        if (!DiskCacheConfig.isEnabled()) return false;
        return dataSize >= DiskCacheConfig.getThresholdBytes()
                && DiskCacheConfig.isAvailable(dataSize);
    }

    /** 清理残留缓存文件（启动时调用） */
    public static void cleanupResidualFiles() {
        cleanupOldCacheFiles(Duration.ofMinutes(5));
    }
}
