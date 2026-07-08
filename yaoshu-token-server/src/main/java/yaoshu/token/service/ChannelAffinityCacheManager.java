package yaoshu.token.service;

import ai.yue.library.data.redis.client.Redis;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CacheUpdate;
import com.alicp.jetcache.anno.Cached;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RKeys;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * 渠道亲和性二级缓存管理器 — L1 Caffeine + L2 Redis（JetCache @Cached BOTH）
 * <p>
 * 为 ChannelAffinityService 的 TtlCache（L1 内存）提供 Redis L2 缓存备份。
 * ChannelAffinityService 在 L1 miss 时通过本管理器查询 L2，写入时同步更新两层。
 * <p>
 * L1 仍由 ChannelAffinityService.TtlCache 自行管理，
 * 本管理器仅负责 JetCache L2 层（自动 Caffeine L1 + Redis L2）。
 */
@Slf4j
@Component
public class ChannelAffinityCacheManager {

    private final Redis redis;

    public ChannelAffinityCacheManager(Redis redis) {
        this.redis = redis;
    }

    /**
     * 注入到静态工具类
     */
    @PostConstruct
    public void init() {
        ChannelAffinityService.setCacheManager(this);
        log.info("ChannelAffinityCacheManager JetCache L2 已就绪");
    }

    // ======================== 亲和性缓存 ========================

    /**
     * 查询渠道亲和性缓存（JetCache L1+L2）      * <p>
     * 返回 null 表示缓存未命中，调用方应使用 L1 TtlCache 结果。
     */
    @Cached(name = "channelAffinity", key = "#cacheKey", cacheType = CacheType.BOTH, expire = 3600)
    public Integer getAffinity(String cacheKey) {
        // Cache-Aside 模式：JetCache 自身管理缓存，方法体返回 null
        // 实际值由 setAffinity 写入
        return null;
    }

    /**
     * 写入渠道亲和性缓存      */
    @CacheUpdate(name = "channelAffinity", key = "#cacheKey", value = "#channelId")
    public void setAffinity(String cacheKey, Integer channelId) {
        // JetCache 自动写入
    }

    /**
     * 删除渠道亲和性缓存      */
    @CacheInvalidate(name = "channelAffinity", key = "#cacheKey")
    public void removeAffinity(String cacheKey) {
        // JetCache 自动删除
    }

    // ======================== 用量统计缓存 ========================

    @Cached(name = "channelAffinityUsage", key = "#cacheKey", cacheType = CacheType.BOTH, expire = 3600)
    public ChannelAffinityService.UsageCacheCounters getUsageStats(String cacheKey) {
        return null;
    }

    @CacheUpdate(name = "channelAffinityUsage", key = "#cacheKey", value = "#counters")
    public void setUsageStats(String cacheKey, ChannelAffinityService.UsageCacheCounters counters) {
        // JetCache 自动写入
    }

    // ======================== Redis 原生操作 — 管理端点统计/清理 ========================

    /**
     * 扫描 JetCache channelAffinity 区域在 Redis 中的所有键      * <p>
     * JetCache 默认 Redis key 格式：{@code jetcache:channelAffinity:*}
     */
    public List<String> scanAffinityKeys() {
        try {
            RKeys keys = redis.getRedisson().getKeys();
            Iterable<String> iterable = keys.getKeysByPattern("jetcache:channelAffinity:*", 1000);
            List<String> result = new ArrayList<>();
            StreamSupport.stream(iterable.spliterator(), false).forEach(result::add);
            return result;
        } catch (Exception e) {
            log.error("扫描渠道亲和性缓存键失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 批量删除 Redis 键      */
    public long deleteKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return 0;
        try {
            return redis.getRedisson().getKeys().delete(keys.toArray(new String[0]));
        } catch (Exception e) {
            log.error("批量删除渠道亲和性缓存键失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 按模式删除 Redis 键      * <p>
     * 先扫描匹配的键，再批量删除。
     *
     * @param pattern Redis glob 模式（如 {@code jetcache:channelAffinity:ruleName:*})
     * @return 删除的键数量
     */
    public long deleteKeysByPattern(String pattern) {
        try {
            RKeys keys = redis.getRedisson().getKeys();
            Iterable<String> iterable = keys.getKeysByPattern(pattern, 1000);
            List<String> matched = new ArrayList<>();
            StreamSupport.stream(iterable.spliterator(), false).forEach(matched::add);
            if (matched.isEmpty()) return 0;
            return keys.delete(matched.toArray(new String[0]));
        } catch (Exception e) {
            log.error("按模式删除渠道亲和性缓存键失败: pattern={}, err={}", pattern, e.getMessage());
            return 0;
        }
    }
}
