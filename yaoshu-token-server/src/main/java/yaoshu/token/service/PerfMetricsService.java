package yaoshu.token.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.ratio.GroupRatioConfig;
import yaoshu.token.mapper.PerfMetricMapper;
import yaoshu.token.mapper.PerfMetricMapper.PerfMetricSummary;
import yaoshu.token.pojo.entity.PerfMetric;

/**
 * 性能指标服务  * <p>
 * 写入路径采用热桶（hotBuckets）内存累加 + 定时 flush 机制：
 * recordRelaySample 先写入内存 AtomicBucket（原子累加），定时 flush 线程
 * 将已完成的时间桶批量 upsert 到 DB。  * 查询路径从 DB 读取历史数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfMetricsService {

    private final PerfMetricMapper perfMetricMapper;

    // ======================== 热桶（内存累加层） ========================

    /** 内存热桶，key = modelName|group|bucketTs*/
    private final ConcurrentHashMap<BucketKey, AtomicBucket> hotBuckets = new ConcurrentHashMap<>();

    /** 时间桶大小（秒）*/
    private static final long BUCKET_SECONDS = 3600;

    /** flush 间隔（毫秒）*/
    private static final long FLUSH_INTERVAL_MS = 5 * 60 * 1000;

    // ======================== Query — 单模型详情 ========================

    /** 查询单模型性能指标 */
    @Cached(name = "perfMetrics:query", key = "#modelName + ':' + #group + ':' + #hours",
            cacheType = CacheType.LOCAL, expire = 300)
    public PerfMetricsResult query(String modelName, String group, int hours) {
        if (hours <= 0) hours = 24;
        if (hours > 24 * 30) hours = 24 * 30;

        long endTs = System.currentTimeMillis() / 1000;
        long startTs = endTs - (long) hours * 3600;

        List<PerfMetric> rows = perfMetricMapper.getPerfMetrics(modelName, group, startTs, endTs);

        // 按分组+时间桶聚合
        Map<String, Map<Long, long[]>> groupBuckets = new LinkedHashMap<>();
        for (PerfMetric row : rows) {
            Map<Long, long[]> buckets = groupBuckets.computeIfAbsent(row.getGroup(), k -> new LinkedHashMap<>());
            long[] agg = buckets.computeIfAbsent(row.getBucketTs(), k -> new long[7]);
            agg[0] += row.getRequestCount();
            agg[1] += row.getSuccessCount();
            agg[2] += row.getTotalLatencyMs();
            agg[3] += row.getTtftSumMs();
            agg[4] += row.getTtftCount();
            agg[5] += row.getOutputTokens();
            agg[6] += row.getGenerationMs();
        }

        // 构建分组结果
        List<GroupResult> groupResults = new ArrayList<>();
        for (var entry : groupBuckets.entrySet()) {
            String grp = entry.getKey();
            Map<Long, long[]> buckets = entry.getValue();

            long totalReq = 0, totalOk = 0, totalLat = 0, totalTtft = 0, totalTtftN = 0, totalOut = 0, totalGen = 0;
            List<BucketPoint> series = new ArrayList<>();

            for (var be : buckets.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                long ts = be.getKey();
                long[] v = be.getValue();
                long req = v[0], ok = v[1], lat = v[2], ttft = v[3], ttftN = v[4], out = v[5], gen = v[6];

                totalReq += req; totalOk += ok; totalLat += lat;
                totalTtft += ttft; totalTtftN += ttftN; totalOut += out; totalGen += gen;

                series.add(new BucketPoint(ts,
                        avg(ttft, ttftN),
                        avg(lat, req),
                        successRate(req, ok),
                        avgTps(out, gen)));
            }

            groupResults.add(new GroupResult(grp,
                    avg(totalTtft, totalTtftN),
                    avg(totalLat, totalReq),
                    successRate(totalReq, totalOk),
                    avgTps(totalOut, totalGen),
                    series));
        }

        return new PerfMetricsResult(modelName, "dbcd0a3c01b55203", filterActiveGroups(groupResults));
    }

    // ======================== QuerySummaryAll — 全局汇总 ========================

    /** 查询全局性能汇总 */
    @Cached(name = "perfMetrics:summaryAll", key = "#hours + ':' + T(yaoshu.token.service.PerfMetricsService).groupsKey(#groups)",
            cacheType = CacheType.LOCAL, expire = 300)
    public SummaryAllResult querySummaryAll(int hours, List<String> groups) {
        if (hours <= 0) hours = 24;
        if (hours > 24 * 30) hours = 24 * 30;

        long endTs = System.currentTimeMillis() / 1000;
        long startTs = endTs - (long) hours * 3600;

        List<PerfMetricSummary> rows = perfMetricMapper.getPerfMetricsSummaryAll(startTs, endTs, groups);

        List<ModelSummary> models = new ArrayList<>();
        for (PerfMetricSummary row : rows) {
            long avgLat = row.getRequestCount() > 0 ? row.getTotalLatencyMs() / row.getRequestCount() : 0;
            double sr = row.getRequestCount() > 0
                    ? (double) row.getSuccessCount() / row.getRequestCount() * 100 : 0;
            double tps = row.getGenerationMs() > 0 && row.getOutputTokens() > 0
                    ? (double) row.getOutputTokens() / (row.getGenerationMs() / 1000.0) : 0;

            models.add(new ModelSummary(row.getModelName(), avgLat,
                    Math.round(sr * 100) / 100.0,
                    Math.round(tps * 100) / 100.0,
                    row.getRequestCount()));
        }
        models.sort((a, b) -> Long.compare(b.requestCount(), a.requestCount()));
        return new SummaryAllResult(models);
    }

    // ======================== 工具方法 ========================

    private long avg(long sum, long count) {
        return count > 0 ? sum / count : 0;
    }

    private double successRate(long req, long ok) {
        return req > 0 ? (double) ok / req * 100 : 0;
    }

    private double avgTps(long outTokens, long genMs) {
        return outTokens > 0 && genMs > 0 ? (double) outTokens / (genMs / 1000.0) : 0;
    }

    /** 过滤活跃分组 */
    private List<GroupResult> filterActiveGroups(List<GroupResult> all) {
        Map<String, Double> active = GroupRatioConfig.getGroupRatioCopy();
        return all.stream()
                .filter(g -> active.containsKey(g.group()) || "auto".equals(g.group()))
                .toList();
    }

    /** 供 @Cached SpEL key 使用：将 groups 列表拼接为字符串键 */
    public static String groupsKey(List<String> groups) {
        if (groups == null || groups.isEmpty()) return "_";
        return String.join(",", groups.stream().sorted().toList());
    }

    // ======================== Relay 采样写入 ========================

    /**
     * 记录单次中继请求的性能采样      * <p>
     * 先写入内存热桶（hotBuckets），由定时 flush 线程批量 upsert 到 DB。
     * 时间桶粒度为 1 小时。      *
     * @param modelName    模型名
     * @param group        分组名
     * @param isStream     是否流式请求
     * @param hasSentResponse 是否已发送过首块响应（流式时有效）
     * @param startTimeMs  请求开始时间（毫秒时间戳）
     * @param firstResponseTimeMs 首字节时间（毫秒时间戳，流式/非流式均有效）
     * @param success      是否成功
     * @param outputTokens 输出的 Token 数
     */
    public void recordRelaySample(String modelName, String group, boolean isStream,
                                   boolean hasSentResponse, long startTimeMs,
                                   long firstResponseTimeMs, boolean success, long outputTokens) {
        if (modelName == null || modelName.isEmpty()) return;
        if (group == null || group.isEmpty()) group = "default";

        long now = System.currentTimeMillis();
        long latencyMs = Math.max(0, now - startTimeMs);

        long ttftMs = 0;
        int ttftCount = 0;
        if (isStream && hasSentResponse && firstResponseTimeMs > startTimeMs) {
            ttftMs = Math.max(0, firstResponseTimeMs - startTimeMs);
            ttftCount = 1;
        }

        long generationMs = latencyMs;
        if (ttftCount > 0) {
            generationMs = Math.max(0, now - firstResponseTimeMs);
        }
        if (generationMs <= 0) generationMs = latencyMs;

        long bucketTs = bucketStart(now / 1000);
        BucketKey key = new BucketKey(modelName, group, bucketTs);

        // 写入内存热桶（原子累加）
        AtomicBucket bucket = hotBuckets.computeIfAbsent(key, k -> new AtomicBucket());
        bucket.add(latencyMs, ttftMs, ttftCount, success, outputTokens, generationMs);
    }

    // ======================== 定时 flush ========================

    /**
     * 定时 flush 热桶到 DB      * <p>
     * 遍历热桶，仅 flush 已完成的时间桶（bucketTs < 当前桶起始时间）。
     * drain 取出原子值并清零，upsert 成功后移除旧桶；失败则将值加回。
     */
    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS)
    public void flushHotBuckets() {
        long currentBucket = bucketStart(System.currentTimeMillis() / 1000);
        long oldCutoff = bucketStart(System.currentTimeMillis() / 1000 - 24 * 3600);

        for (var entry : hotBuckets.entrySet()) {
            BucketKey key = entry.getKey();
            // 仅 flush 已完成的时间桶
            if (key.bucketTs() >= currentBucket) continue;

            AtomicBucket bucket = entry.getValue();
            long[] drained = bucket.drain();
            if (drained[0] == 0) {
                // 空桶，超过 24h 则清理
                if (key.bucketTs() < oldCutoff) {
                    hotBuckets.remove(key);
                }
                continue;
            }

            PerfMetric p = new PerfMetric();
            p.setModelName(key.modelName());
            p.setGroup(key.group());
            p.setBucketTs(key.bucketTs());
            p.setRequestCount(drained[0]);
            p.setSuccessCount(drained[1]);
            p.setTotalLatencyMs(drained[2]);
            p.setTtftSumMs(drained[3]);
            p.setTtftCount(drained[4]);
            p.setOutputTokens(drained[5]);
            p.setGenerationMs(drained[6]);

            try {
                perfMetricMapper.upsertPerfMetricBatch(p);
                // flush 成功，超过 24h 的旧桶清理，否则保留空桶（与 Go deleteOldEmptyBucket 一致）
                if (key.bucketTs() < oldCutoff) {
                    hotBuckets.remove(key);
                }
            } catch (Exception e) {
                // flush 失败，将值加回桶中等待下次 flush
                bucket.addCounters(drained);
                log.warn("性能指标批量 flush 失败: model={}, group={}, bucketTs={}",
                        key.modelName(), key.group(), key.bucketTs(), e);
            }
        }
    }

    /** 计算时间桶起始时间戳 */
    private static long bucketStart(long epochSeconds) {
        return epochSeconds - (epochSeconds % BUCKET_SECONDS);
    }

    // ======================== 结果类型（record） ========================

    public record PerfMetricsResult(String modelName, String seriesSchema, List<GroupResult> groups) {}
    public record GroupResult(String group, long avgTtftMs, long avgLatencyMs,
                               double successRate, double avgTps, List<BucketPoint> series) {}
    public record BucketPoint(long ts, long avgTtftMs, long avgLatencyMs, double successRate, double avgTps) {}
    public record SummaryAllResult(List<ModelSummary> models) {}
    public record ModelSummary(String modelName, long avgLatencyMs, double successRate,
                                double avgTps, long requestCount) {}

    // ======================== 热桶内部类型 ========================

    /** 热桶 key*/
    private record BucketKey(String modelName, String group, long bucketTs) {}

    /**
     * 原子计数桶      * <p>
     * 使用 AtomicLong 原子累加 count / duration，drain 时 Swap 清零。
     */
    private static class AtomicBucket {
        private final AtomicLong requestCount = new AtomicLong();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong totalLatencyMs = new AtomicLong();
        private final AtomicLong ttftSumMs = new AtomicLong();
        private final AtomicLong ttftCount = new AtomicLong();
        private final AtomicLong outputTokens = new AtomicLong();
        private final AtomicLong generationMs = new AtomicLong();

        /** 原子累加单次采样*/
        void add(long latencyMs, long ttftMs, int ttftCnt, boolean success,
                 long outTokens, long genMs) {
            requestCount.incrementAndGet();
            if (success) successCount.incrementAndGet();
            if (latencyMs > 0) totalLatencyMs.addAndGet(latencyMs);
            if (ttftCnt > 0 && ttftMs >= 0) {
                ttftSumMs.addAndGet(ttftMs);
                ttftCount.addAndGet(ttftCnt);
            }
            if (outTokens > 0 && genMs > 0) {
                outputTokens.addAndGet(outTokens);
                generationMs.addAndGet(genMs);
            }
        }

        /**
         * 取出原子值并清零          *
         * @return [req, ok, lat, ttftSum, ttftN, outTokens, genMs]
         */
        long[] drain() {
            return new long[] {
                requestCount.getAndSet(0),
                successCount.getAndSet(0),
                totalLatencyMs.getAndSet(0),
                ttftSumMs.getAndSet(0),
                ttftCount.getAndSet(0),
                outputTokens.getAndSet(0),
                generationMs.getAndSet(0)
            };
        }

        /** flush 失败时将值加回*/
        void addCounters(long[] c) {
            if (c[0] != 0) requestCount.addAndGet(c[0]);
            if (c[1] != 0) successCount.addAndGet(c[1]);
            if (c[2] != 0) totalLatencyMs.addAndGet(c[2]);
            if (c[3] != 0) ttftSumMs.addAndGet(c[3]);
            if (c[4] != 0) ttftCount.addAndGet(c[4]);
            if (c[5] != 0) outputTokens.addAndGet(c[5]);
            if (c[6] != 0) generationMs.addAndGet(c[6]);
        }
    }
}
