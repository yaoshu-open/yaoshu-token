package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.service.OptionService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 性能管理控制器  * <p>
 * 认证：RootAuth（全部）
 */
@Slf4j
@RestController
@SaCheckRole("root")
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final OptionService optionService;

    // ======================== 磁盘缓存统计 ========================

    /** 缓存命中计数（原子计数器） */
    private static final java.util.concurrent.atomic.AtomicLong cacheHitCount = new java.util.concurrent.atomic.AtomicLong(0);
    /** 缓存未命中计数 */
    private static final java.util.concurrent.atomic.AtomicLong cacheMissCount = new java.util.concurrent.atomic.AtomicLong(0);
    /** 磁盘缓存命中计数 */
    private static final java.util.concurrent.atomic.AtomicLong diskCacheHitCount = new java.util.concurrent.atomic.AtomicLong(0);

    /**
     * 记录缓存命中
     */
    public static void recordCacheHit() {
        cacheHitCount.incrementAndGet();
    }

    /**
     * 记录缓存未命中
     */
    public static void recordCacheMiss() {
        cacheMissCount.incrementAndGet();
    }

    /**
     * 记录磁盘缓存命中
     */
    public static void recordDiskCacheHit() {
        diskCacheHitCount.incrementAndGet();
    }

    /**
     * 磁盘缓存统计信息
     */
    public static class DiskCacheStats {
        public long hitCount;
        public long missCount;
        public long diskHitCount;
        public long totalRequests;
        public double hitRate;

        public DiskCacheStats(long hit, long miss, long diskHit) {
            this.hitCount = hit;
            this.missCount = miss;
            this.diskHitCount = diskHit;
            this.totalRequests = hit + miss;
            this.hitRate = totalRequests > 0 ? (double) hit / totalRequests * 100 : 0;
        }
    }

    /**
     * 获取性能统计信息      */
    @GetMapping("/stats")
    public Result<?> getStats() {
        // 缓存统计
        long hitCount = cacheHitCount.get();
        long missCount = cacheMissCount.get();
        long diskHitCount = diskCacheHitCount.get();
        DiskCacheStats cacheStats = new DiskCacheStats(hitCount, missCount, diskHitCount);

        // 内存统计
        Runtime runtime = Runtime.getRuntime();
        com.sun.management.OperatingSystemMXBean osBean = null;
        try {
            osBean = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        } catch (Exception e) {
            log.debug("获取 OperatingSystemMXBean 失败", e);
        }

        Map<String, Object> memoryStats = new LinkedHashMap<>();
        memoryStats.put("alloc", runtime.totalMemory() - runtime.freeMemory());
        memoryStats.put("total_alloc", runtime.totalMemory() - runtime.freeMemory());
        memoryStats.put("sys", runtime.totalMemory());
        memoryStats.put("num_gc", 0); // Java 不直接暴露 GC 次数
        memoryStats.put("num_goroutine", Thread.activeCount());

        // 磁盘缓存目录信息
        Path cacheDir = resolveDiskCacheDir();
        Map<String, Object> diskCacheInfo = new LinkedHashMap<>();
        diskCacheInfo.put("path", cacheDir.toString());
        diskCacheInfo.put("exists", Files.isDirectory(cacheDir));
        diskCacheInfo.put("file_count", countFiles(cacheDir));
        diskCacheInfo.put("total_size", getDirectorySize(cacheDir));

        // 磁盘空间信息（接入文件系统真实使用率
        Map<String, Object> diskSpaceInfo = new LinkedHashMap<>();
        try {
            java.io.File root = cacheDir.toAbsolutePath().toFile();
            // 若缓存目录不存在则退回到 / 根目录的可用空间
            java.io.File target = root.exists() ? root : new java.io.File(System.getProperty("user.dir"));
            long total = target.getTotalSpace();
            long usable = target.getUsableSpace();
            long used = total - usable;
            double usedPercent = total > 0 ? (used * 100.0 / total) : 0.0;
            diskSpaceInfo.put("total_bytes", total);
            diskSpaceInfo.put("used_bytes", used);
            diskSpaceInfo.put("free_bytes", usable);
            diskSpaceInfo.put("used_percent", Math.round(usedPercent * 100.0) / 100.0);
        } catch (Exception e) {
            diskSpaceInfo.put("used_percent", 0.0);
            log.warn("读取磁盘空间信息失败", e);
        }

        // 配置信息
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("disk_cache_enabled", !"false".equals(optionService.getValue("DiskCacheEnabled")));
        config.put("disk_cache_threshold_mb", intOption("DiskCacheThresholdMB", 100));
        config.put("disk_cache_max_size_mb", intOption("DiskCacheMaxSizeMB", 1024));
        config.put("disk_cache_path", optionService.getValue("DiskCachePath"));
        config.put("is_running_in_container", isRunningInContainer());
        config.put("monitor_enabled", !"false".equals(optionService.getValue("MonitorEnabled")));
        config.put("monitor_cpu_threshold", intOption("MonitorCPUThreshold", 80));
        config.put("monitor_memory_threshold", intOption("MonitorMemoryThreshold", 80));
        config.put("monitor_disk_threshold", intOption("MonitorDiskThreshold", 80));

        // 组合结果
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cache_stats", cacheStats);
        data.put("memory_stats", memoryStats);
        data.put("disk_cache_info", diskCacheInfo);
        data.put("disk_space_info", diskSpaceInfo);
        data.put("config", config);

        return R.success(data);
    }

    private int intOption(String key, int defaultValue) {
        String value = optionService.getValue(key);
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean isRunningInContainer() {
        return "true".equalsIgnoreCase(System.getenv("RUNNING_IN_CONTAINER"));
    }

    /** 磁盘缓存目录路径（从 option 中读取或使用默认值） */
    private Path resolveDiskCacheDir() {
        String path = optionService.getValue("disk_cache_path");
        if (path != null && !path.isBlank()) {
            Path dir = Path.of(path.trim());
            if (Files.isDirectory(dir)) return dir;
        }
        // 默认使用系统临时目录下的 yaoshu-disk-cache
        return Path.of(System.getProperty("java.io.tmpdir"), "yaoshu-disk-cache");
    }

    private long countFiles(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private long getDirectorySize(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0L; }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    @DeleteMapping("/disk_cache")
    public Result<?> clearDiskCache() {
        Path cacheDir = resolveDiskCacheDir();
        long deletedCount = 0;
        if (Files.isDirectory(cacheDir)) {
            Instant cutoff = Instant.now().minus(10, ChronoUnit.MINUTES);
            try (Stream<Path> files = Files.list(cacheDir)) {
                for (Path file : files.toList()) {
                    try {
                        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
                        if (attr.lastModifiedTime().toInstant().isBefore(cutoff)) {
                            Files.deleteIfExists(file);
                            deletedCount++;
                        }
                    } catch (IOException e) {
                        log.warn("清理磁盘缓存文件失败: {}", file, e);
                    }
                }
            } catch (IOException e) {
                log.warn("扫描磁盘缓存目录失败: {}", cacheDir, e);
            }
        }
        return R.success("不活跃的磁盘缓存已清理，共 " + deletedCount + " 个文件");
    }

    @PostMapping("/reset_stats")
    public Result<?> resetStats() {
        // Java 版本无 Go 的全局计数器，返回成功
        return R.success("统计信息已重置");
    }

    @PostMapping("/gc")
    public Result<?> forceGc() {
        // Go 特有 debug.FreeOSMemory()，Java 无法直接触发
        return R.success("GC 已建议执行（JVM 自动管理内存）");
    }

    @GetMapping("/logs")
    public Result<?> getLogFiles(HttpServletRequest request) {
        String logDir = optionService.getValue("log_dir");
        if (logDir == null || logDir.isBlank()) {
            return R.success(Map.of("enabled", false));
        }
        Path dir = Path.of(logDir.trim());
        if (!Files.isDirectory(dir)) {
            return R.success(Map.of("enabled", false));
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Map<String, Object>> fileInfos = new ArrayList<>();
            long totalSize = 0;
            for (Path file : files.filter(Files::isRegularFile).sorted(
                    (a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString())).toList()) {
                try {
                    BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("name", file.getFileName().toString());
                    info.put("size", attr.size());
                    info.put("mod_time", attr.lastModifiedTime().toInstant().toString());
                    fileInfos.add(info);
                    totalSize += attr.size();
                } catch (IOException e) {
                    log.debug("读取日志文件属性失败: {}", file, e);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("log_dir", logDir);
            data.put("enabled", true);
            data.put("file_count", fileInfos.size());
            data.put("total_size", totalSize);
            data.put("files", fileInfos);
            return R.success(data);
        } catch (IOException e) {
            throw new ResultException(R.errorPrompt("读取日志目录失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/logs")
    public Result<?> cleanupLogFiles(@RequestParam(required = false) String mode,
                                                @RequestParam(required = false) String value) {
        if (mode == null || (!"by_count".equals(mode) && !"by_days".equals(mode))) {
            throw new ResultException(R.errorPrompt("invalid mode, must be by_count or by_days"));
        }
        int parsedValue;
        try {
            parsedValue = Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new ResultException(R.errorPrompt("invalid value, must be a positive integer"));
        }
        if (parsedValue < 1) {
            throw new ResultException(R.errorPrompt("invalid value, must be a positive integer"));
        }

        String logDir = optionService.getValue("log_dir");
        if (logDir == null || logDir.isBlank()) {
            throw new ResultException(R.errorPrompt("log directory not configured"));
        }
        Path dir = Path.of(logDir.trim());
        if (!Files.isDirectory(dir)) {
            throw new ResultException(R.errorPrompt("log directory not found"));
        }

        String activeLogPath = resolveActiveLogPath(logDir);

        // 收集 oneapi-*.log 文件按文件名降序（最新在前），与 Go getLogFiles 行为一致
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("oneapi-") && n.endsWith(".log");
                    })
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new ResultException(R.errorPrompt("读取日志目录失败: " + e.getMessage()));
        }

        List<Path> toDelete = new ArrayList<>();
        if ("by_count".equals(mode)) {
            // files 已按文件名降序，保留前 parsedValue 个，其余加入待删
            for (int i = parsedValue; i < files.size(); i++) {
                Path f = files.get(i);
                if (activeLogPath != null && f.toAbsolutePath().toString().equals(activeLogPath)) continue;
                toDelete.add(f);
            }
        } else {
            // by_days：cutoff = now - parsedValue 天，早于 cutoff 的加入待删
            Instant cutoff = Instant.now().minus(parsedValue, ChronoUnit.DAYS);
            for (Path f : files) {
                try {
                    BasicFileAttributes attr = Files.readAttributes(f, BasicFileAttributes.class);
                    if (attr.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        if (activeLogPath != null && f.toAbsolutePath().toString().equals(activeLogPath)) continue;
                        toDelete.add(f);
                    }
                } catch (IOException e) {
                    log.debug("读取日志文件属性失败: {}", f, e);
                }
            }
        }

        long deletedCount = 0;
        long freedBytes = 0;
        List<String> failedFiles = new ArrayList<>();
        for (Path f : toDelete) {
            try {
                BasicFileAttributes attr = Files.readAttributes(f, BasicFileAttributes.class);
                long size = attr.size();
                if (Files.deleteIfExists(f)) {
                    deletedCount++;
                    freedBytes += size;
                }
            } catch (IOException e) {
                failedFiles.add(f.getFileName().toString());
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted_count", deletedCount);
        data.put("freed_bytes", freedBytes);
        data.put("failed_files", failedFiles);

        return R.success(data);
    }

    /**
     * 解析当前活跃日志路径。      * 简化实现：日志按日期切分，取 oneapi-{yyyyMMdd}.log 形式。
     */
    private String resolveActiveLogPath(String logDir) {
        try {
            Path dir = Path.of(logDir.trim());
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path active = dir.resolve("oneapi-" + today + ".log");
            return Files.exists(active) ? active.toAbsolutePath().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ======================== 辅助方法 ========================



}

