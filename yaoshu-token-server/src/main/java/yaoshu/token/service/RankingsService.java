package yaoshu.token.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.mapper.QuotaMapper;
import yaoshu.token.mapper.QuotaMapper.RankingQuotaBucket;
import yaoshu.token.mapper.QuotaMapper.RankingQuotaTotal;
import yaoshu.token.pojo.vo.PricingVO;
import yaoshu.token.pojo.vo.PricingVendorVO;

/**
 * 排名统计服务  * <p>
 * 核心职责：基于 quota_data 表的 Token 用量数据，生成模型与供应商排行榜快照，包含时序历史与供应商份额序列。
 * 缓存策略：JetCache {@code @Cached} L1（Caffeine），300 秒 TTL（Go 原为 5 分钟）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingsService {

    private final QuotaMapper quotaMapper;
    private final PricingService pricingService;

    private static final int LEADERBOARD_LIMIT = 20;
    private static final int MOVER_LIMIT = 6;
    private static final int HISTORY_LIMIT = 10;
    private static final int VENDOR_LIMIT = 5;
    private static final String UNKNOWN_VENDOR = "Unknown";
    private static final String OTHERS_LABEL = "Others";

    /**
     * 获取排行榜快照（JetCache LOCAL 300s 缓存）      */
    @Cached(name = "rankings", key = "#period", cacheType = CacheType.LOCAL, expire = 300)
    public RankingsSnapshot getRankingsSnapshot(String period) {
        PeriodConfig config = periodConfig(period);
        long now = System.currentTimeMillis() / 1000;
        long startTime = config.duration > 0 ? now - config.duration : 0;
        long endTime = now;

        // 查询聚合数据
        List<RankingQuotaTotal> totals = quotaMapper.getRankingQuotaTotals(startTime, endTime > 0 ? endTime : null);
        List<RankingQuotaBucket> buckets = quotaMapper.getRankingQuotaBuckets(startTime, endTime > 0 ? endTime : null, config.bucketSize());

        // 构建排名
        return buildSnapshot(totals, buckets, startTime, config);
    }

    private RankingsSnapshot buildSnapshot(List<RankingQuotaTotal> totals, List<RankingQuotaBucket> buckets, long startTime, PeriodConfig config) {
        if (totals.isEmpty()) {
            return emptySnapshot();
        }

        // 查询上期数据用于变化率
        Map<String, Long> previousTokens = Map.of();
        Map<String, Integer> previousRanks = Map.of();
        if (config.hasPrevious && startTime > 0) {
            long prevEnd = startTime - 1;
            long prevStart = prevEnd - config.duration;
            List<RankingQuotaTotal> prevTotals = quotaMapper.getRankingQuotaTotals(prevStart > 0 ? prevStart : null, prevEnd);
            Map<String, Long> ptMap = new LinkedHashMap<>();
            Map<String, Integer> prMap = new LinkedHashMap<>();
            int rank = 0;
            for (RankingQuotaTotal t : prevTotals) {
                rank++;
                ptMap.put(t.getModelName(), t.getTotalTokens());
                prMap.put(t.getModelName(), rank);
            }
            previousTokens = ptMap;
            previousRanks = prMap;
        }

        // 获取元数据
        List<PricingVO> pricing = pricingService.getPricing();
        List<PricingVendorVO> vendors = pricingService.getVendors();
        Map<Integer, String> vendorNameById = new LinkedHashMap<>();
        Map<Integer, String> vendorIconById = new LinkedHashMap<>();
        for (PricingVendorVO v : vendors) {
            vendorNameById.put(v.getId(), v.getName());
            vendorIconById.put(v.getId(), v.getIcon());
        }

        // 模型名 → 供应商映射
        Map<String, String> modelVendor = new LinkedHashMap<>();
        Map<String, String> modelVendorIcon = new LinkedHashMap<>();
        for (PricingVO p : pricing) {
            String vName = p.getVendorId() != null ? vendorNameById.getOrDefault(p.getVendorId(), UNKNOWN_VENDOR) : UNKNOWN_VENDOR;
            String vIcon = p.getVendorId() != null ? vendorIconById.getOrDefault(p.getVendorId(), "") : "";
            modelVendor.put(p.getModelName(), vName);
            modelVendorIcon.put(p.getModelName(), vIcon);
        }

        // 总 Token
        long totalTokens = totals.stream().mapToLong(RankingQuotaTotal::getTotalTokens).sum();

        // 构建模型排名
        List<RankedModel> models = new ArrayList<>();
        int rank = 0;
        for (RankingQuotaTotal t : totals) {
            rank++;
            String vendor = modelVendor.getOrDefault(t.getModelName(), UNKNOWN_VENDOR);
            String icon = modelVendorIcon.getOrDefault(t.getModelName(), "");

            Integer previousRank = previousRanks.get(t.getModelName());
            Long prevTokens = previousTokens.get(t.getModelName());
            double growth = config.hasPrevious ? growthPct(t.getTotalTokens(), prevTokens) : 0;

            models.add(new RankedModel(
                    rank,
                    previousRank,
                    t.getModelName(),
                    vendor,
                    icon,
                    t.getTotalTokens(),
                    share(t.getTotalTokens(), totalTokens),
                    growth
            ));
        }

        // 构建供应商排名
        Map<String, VendorAgg> vendorAggMap = new LinkedHashMap<>();
        for (RankedModel m : models) {
            VendorAgg agg = vendorAggMap.computeIfAbsent(m.vendor, VendorAgg::new);
            agg.icon = m.vendorIcon;
            agg.totalTokens += m.totalTokens;
            agg.modelCount++;
            if (agg.topModel == null || m.totalTokens > agg.topModelTokens) {
                agg.topModel = m.modelName;
                agg.topModelTokens = m.totalTokens;
            }
            Long prev = previousTokens.get(m.modelName);
            if (prev != null) agg.previousTokens += prev;
        }

        List<RankedVendor> vendorList = new ArrayList<>();
        int vRank = 0;
        for (VendorAgg agg : vendorAggMap.values()) {
            if (agg.totalTokens <= 0) continue;
            vRank++;
            double growth = config.hasPrevious ? growthPct(agg.totalTokens, agg.previousTokens) : 0;
            vendorList.add(new RankedVendor(
                    vRank, agg.name, agg.icon, agg.totalTokens,
                    share(agg.totalTokens, totalTokens), growth, agg.modelCount, agg.topModel
            ));
        }

        // 构建移动榜
        List<RankingMover> movers = new ArrayList<>();
        List<RankingMover> droppers = new ArrayList<>();
        for (RankedModel m : models) {
            if (m.previousRank == null) {
                // 新上榜：上期无数据（冷启动或新模型接入），放入上升榜顶部
                movers.add(new RankingMover(m.modelName, m.vendor, m.vendorIcon, 0, m.rank, m.growthPct, true));
                continue;
            }
            int delta = m.previousRank - m.rank;
            if (delta == 0) continue;
            RankingMover mover = new RankingMover(m.modelName, m.vendor, m.vendorIcon, delta, m.rank, m.growthPct, false);
            if (delta > 0) movers.add(mover);
            else droppers.add(mover);
        }
        // 排序：新上榜最优先，其次按 rankDelta 降序
        movers.sort((a, b) -> {
            if (a.isNewEntry() != b.isNewEntry()) return a.isNewEntry() ? -1 : 1;
            return b.rankDelta() != a.rankDelta() ? b.rankDelta() - a.rankDelta() : Double.compare(b.growthPct(), a.growthPct());
        });
        droppers.sort((a, b) -> a.rankDelta() != b.rankDelta() ? a.rankDelta() - b.rankDelta() : Double.compare(a.growthPct(), b.growthPct()));

        ModelHistorySeries modelHistory = buildModelHistory(buckets, totals, modelVendor, config);
        VendorShareSeries vendorShareHistory = buildVendorShareHistory(buckets, vendorList, totalTokens, modelVendor, config);

        return new RankingsSnapshot(
                limit(models, LEADERBOARD_LIMIT),
                vendorList,
                limit(movers, MOVER_LIMIT),
                limit(droppers, MOVER_LIMIT),
                modelHistory,
                vendorShareHistory
        );
    }

    // ======================== 工具方法 ========================

    private double share(long value, long total) {
        if (total <= 0 || value <= 0) return 0;
        return Math.round((double) value / total * 10000) / 10000.0;
    }

    private double growthPct(long current, Long previous) {
        if (previous == null || previous <= 0) return current > 0 ? 100 : 0;
        return Math.round((double) (current - previous) / previous * 10000) / 10000.0;
    }

    private <T> List<T> limit(List<T> list, int limit) {
        if (list.size() <= limit) return list;
        return list.subList(0, limit);
    }

    /**
     * 构建模型时序历史序列。      * <p>
     * 选取 Top {@value #HISTORY_LIMIT} 模型，其余归入 Others；按时间桶聚合每个模型的 Token 变化。
     */
    private ModelHistorySeries buildModelHistory(List<RankingQuotaBucket> buckets, List<RankingQuotaTotal> totals,
                                                  Map<String, String> modelVendor, PeriodConfig config) {
        Set<String> topModels = new LinkedHashSet<>();
        List<ModelHistoryModel> models = new ArrayList<>(Math.min(totals.size(), HISTORY_LIMIT) + 1);
        long otherTotal = 0;
        for (int idx = 0; idx < totals.size(); idx++) {
            RankingQuotaTotal item = totals.get(idx);
            if (idx < HISTORY_LIMIT) {
                topModels.add(item.getModelName());
                String vendor = modelVendor.getOrDefault(item.getModelName(), UNKNOWN_VENDOR);
                models.add(new ModelHistoryModel(item.getModelName(), vendor, item.getTotalTokens()));
            } else {
                otherTotal += item.getTotalTokens();
            }
        }
        if (otherTotal > 0) {
            models.add(new ModelHistoryModel(OTHERS_LABEL, "Various", otherTotal));
        }

        TreeSet<Long> bucketSet = new TreeSet<>();
        Map<Long, Map<String, Long>> tokensByBucketAndModel = new LinkedHashMap<>();
        for (RankingQuotaBucket item : buckets) {
            String modelName = topModels.contains(item.getModelName()) ? item.getModelName() : OTHERS_LABEL;
            bucketSet.add(item.getBucket());
            tokensByBucketAndModel.computeIfAbsent(item.getBucket(), k -> new LinkedHashMap<>())
                    .merge(modelName, item.getTokens(), Long::sum);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(config.labelLayout(), Locale.ENGLISH);
        List<ModelHistoryPoint> points = new ArrayList<>(bucketSet.size() * models.size());
        for (long bucket : bucketSet) {
            Map<String, Long> modelTokens = tokensByBucketAndModel.getOrDefault(bucket, Map.of());
            for (ModelHistoryModel hm : models) {
                Long tokens = modelTokens.get(hm.name());
                if (tokens == null || tokens <= 0) continue;
                points.add(new ModelHistoryPoint(
                        bucketTs(bucket),
                        bucketLabel(bucket, formatter),
                        hm.name(),
                        hm.vendor(),
                        tokens
                ));
            }
        }

        return new ModelHistorySeries(points, models, bucketSet.size());
    }

    /**
     * 构建供应商份额时序序列。      * <p>
     * 选取 Top {@value #VENDOR_LIMIT} 供应商，其余归入 Others；按时间桶计算每个供应商在该桶内的份额。
     */
    private VendorShareSeries buildVendorShareHistory(List<RankingQuotaBucket> buckets, List<RankedVendor> vendors,
                                                      long totalTokens, Map<String, String> modelVendor, PeriodConfig config) {
        Set<String> topVendors = new LinkedHashSet<>();
        List<VendorShareVendor> vendorRows = new ArrayList<>(Math.min(vendors.size(), VENDOR_LIMIT) + 1);
        long otherTotal = 0;
        for (int idx = 0; idx < vendors.size(); idx++) {
            RankedVendor vendor = vendors.get(idx);
            if (idx < VENDOR_LIMIT) {
                topVendors.add(vendor.vendor());
                vendorRows.add(new VendorShareVendor(vendor.vendor(), vendor.totalTokens(), vendor.share()));
            } else {
                otherTotal += vendor.totalTokens();
            }
        }
        if (otherTotal > 0) {
            vendorRows.add(new VendorShareVendor(OTHERS_LABEL, otherTotal, share(otherTotal, totalTokens)));
        }

        TreeSet<Long> bucketSet = new TreeSet<>();
        Map<Long, Map<String, Long>> tokensByBucketAndVendor = new LinkedHashMap<>();
        Map<Long, Long> totalsByBucket = new LinkedHashMap<>();
        for (RankingQuotaBucket item : buckets) {
            String vendorName = modelVendor.getOrDefault(item.getModelName(), UNKNOWN_VENDOR);
            if (!topVendors.contains(vendorName)) {
                vendorName = OTHERS_LABEL;
            }
            bucketSet.add(item.getBucket());
            tokensByBucketAndVendor.computeIfAbsent(item.getBucket(), k -> new LinkedHashMap<>())
                    .merge(vendorName, item.getTokens(), Long::sum);
            totalsByBucket.merge(item.getBucket(), item.getTokens(), Long::sum);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(config.labelLayout(), Locale.ENGLISH);
        List<VendorSharePoint> points = new ArrayList<>(bucketSet.size() * vendorRows.size());
        for (long bucket : bucketSet) {
            Map<String, Long> vendorTokens = tokensByBucketAndVendor.getOrDefault(bucket, Map.of());
            long bucketTotal = totalsByBucket.getOrDefault(bucket, 0L);
            for (VendorShareVendor vr : vendorRows) {
                Long tokens = vendorTokens.get(vr.name());
                if (tokens == null || tokens <= 0) continue;
                points.add(new VendorSharePoint(
                        bucketTs(bucket),
                        bucketLabel(bucket, formatter),
                        vr.name(),
                        share(tokens, bucketTotal),
                        tokens
                ));
            }
        }

        return new VendorShareSeries(points, vendorRows, bucketSet.size());
    }

    /** 桶起始时间戳的 RFC3339（UTC）字符串 */
    private String bucketTs(long bucket) {
        return Instant.ofEpochSecond(bucket).toString();
    }

    /** 桶起始时间的本地化标签 */
    private String bucketLabel(long bucket, DateTimeFormatter formatter) {
        return Instant.ofEpochSecond(bucket).atZone(ZoneId.systemDefault()).format(formatter);
    }

    private RankingsSnapshot emptySnapshot() {
        return new RankingsSnapshot(List.of(), List.of(), List.of(), List.of(),
                new ModelHistorySeries(List.of(), List.of(), 0),
                new VendorShareSeries(List.of(), List.of(), 0));
    }

    private PeriodConfig periodConfig(String period) {
        return switch (period) {
            case "today" -> new PeriodConfig("today", 24 * 3600L, 3600L, "HH:mm", true);
            case "month" -> new PeriodConfig("month", 30 * 24 * 3600L, 24 * 3600L, "MMM d", true);
            case "year"  -> new PeriodConfig("year", 365 * 24 * 3600L, 7 * 24 * 3600L, "MMM d", true);
            case "all"   -> new PeriodConfig("all", 0L, 30 * 24 * 3600L, "MMM yyyy", false);
            default      -> new PeriodConfig("week", 7 * 24 * 3600L, 24 * 3600L, "MMM d", true);
        };
    }

    // ======================== 内嵌类型 ========================

    private record PeriodConfig(String id, long duration, long bucketSize, String labelLayout, boolean hasPrevious) {}

    private static class VendorAgg {
        String name, icon;
        long totalTokens, previousTokens;
        int modelCount;
        String topModel;
        long topModelTokens;

        VendorAgg(String name) { this.name = name; }
    }

    /** 排行榜快照 */
    public record RankingsSnapshot(
            List<RankedModel> models,
            List<RankedVendor> vendors,
            List<RankingMover> topMovers,
            List<RankingMover> topDroppers,
            ModelHistorySeries modelsHistory,
            VendorShareSeries vendorShareHistory
    ) {}

    /** 模型用量时序单点 */
    public record ModelHistoryPoint(
            String ts,
            String label,
            String model,
            String vendor,
            long tokens
    ) {}

    /** 模型用量时序中的模型行 */
    public record ModelHistoryModel(
            String name,
            String vendor,
            long total
    ) {}

    /** 模型用量时序序列 */
    public record ModelHistorySeries(
            List<ModelHistoryPoint> points,
            List<ModelHistoryModel> models,
            int buckets
    ) {}

    /** 供应商市场份额时序单点 */
    public record VendorSharePoint(
            String ts,
            String label,
            String vendor,
            double share,
            long tokens
    ) {}

    /** 供应商市场份额时序中的供应商行 */
    public record VendorShareVendor(
            String name,
            long total,
            double share
    ) {}

    /** 供应商市场份额时序序列 */
    public record VendorShareSeries(
            List<VendorSharePoint> points,
            List<VendorShareVendor> vendors,
            int buckets
    ) {}

    public record RankedModel(
            int rank,
            Integer previousRank,
            String modelName,
            String vendor,
            String vendorIcon,
            long totalTokens,
            double share,
            double growthPct
    ) {}

    public record RankedVendor(
            int rank,
            String vendor,
            String vendorIcon,
            long totalTokens,
            double share,
            double growthPct,
            int modelsCount,
            String topModel
    ) {}

    public record RankingMover(
            String modelName,
            String vendor,
            String vendorIcon,
            int rankDelta,
            int currentRank,
            double growthPct,
            boolean isNewEntry
    ) {}
}
