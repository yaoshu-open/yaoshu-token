package yaoshu.token.service;

import ai.yue.library.data.redis.client.Redis;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.EnvConstants;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知限流服务  * <p>
 * 防止短时间内重复发送相同通知（如额度不足警告）。
 * 使用 yue-library Redis 客户端实现分布式限流（多实例共享），Redis 不可用时回退到内存限流。
 * <p>
 * Key 格式：notify_limit:{userId}:{notifyType}:{yyyyMMddHH}（小时级时间窗口）
 */
@Slf4j
@Service
public class NotifyLimitService {

    private final Redis redis;

    /** 内存限流回退存储：key → 计数条目 */
    private final ConcurrentHashMap<String, MemoryLimitEntry> memoryStore = new ConcurrentHashMap<>();

    /** 小时级时间窗口格式 */
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    /** 默认通知限制次数*/
    private static final int DEFAULT_NOTIFY_LIMIT_COUNT = 2;

    /** 默认通知限制时长（分钟）*/
    private static final int DEFAULT_NOTIFY_LIMIT_DURATION_MINUTE = 10;

    public NotifyLimitService(Redis redis) {
        this.redis = redis;
    }

    /**
     * 检查通知是否超过限制      *
     * @param userId     用户 ID
     * @param notifyType 通知类型（如 "quota_warning"）
     * @return true=可以发送，false=超过限制
     */
    public boolean checkNotificationLimit(int userId, String notifyType) {
        int limit = getNotifyLimitCount();
        int durationMinute = getNotificationLimitDurationMinute();
        String key = buildKey(userId, notifyType);

        // 优先使用 Redis
        try {
            return checkRedisLimit(key, limit, durationMinute);
        } catch (Exception e) {
            log.warn("Redis 限流不可用，回退到内存限流: {}", e.getMessage());
            return checkMemoryLimit(userId, notifyType, limit, durationMinute);
        }
    }

    // ======================== Redis 限流 ========================

    /**
     * Redis 限流检查      * <p>
     * Key 不存在时初始化为 1 并设置 TTL；已达限制返回 false；未达限制原子递增。
     */
    private boolean checkRedisLimit(String key, int limit, int durationMinute) {
        RAtomicLong counter = redis.getRedisson().getAtomicLong(key);

        // Key 不存在，初始化为 1 并设置 TTL
        if (!counter.isExists()) {
            counter.set(1);
            counter.expire(Duration.ofMinutes(durationMinute));
            return true;
        }

        long currentCount = counter.get();

        // 已达限制
        if (currentCount >= limit) {
            return false;
        }

        // 未达限制，递增
        counter.incrementAndGet();
        return true;
    }

    // ======================== 内存限流（Redis 不可用时回退） ========================

    /**
     * 内存限流检查      */
    private boolean checkMemoryLimit(int userId, String notifyType, int limit, int durationMinute) {
        String memKey = userId + ":" + notifyType + ":" + LocalDateTime.now().format(HOUR_FORMAT);
        LocalDateTime now = LocalDateTime.now();
        Duration ttl = Duration.ofMinutes(durationMinute);

        MemoryLimitEntry entry = memoryStore.compute(memKey, (k, existing) -> {
            if (existing == null || Duration.between(existing.timestamp, now).compareTo(ttl) >= 0) {
                return new MemoryLimitEntry(1, now);
            }
            existing.count++;
            return existing;
        });

        return entry.count <= limit;
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 构建 Redis key      */
    private String buildKey(int userId, String notifyType) {
        String hourPart = LocalDateTime.now().format(HOUR_FORMAT);
        return "notify_limit:" + userId + ":" + notifyType + ":" + hourPart;
    }

    /**
     * 获取通知限制次数      */
    private int getNotifyLimitCount() {
        return EnvConstants.notifyLimitCount > 0 ? EnvConstants.notifyLimitCount : DEFAULT_NOTIFY_LIMIT_COUNT;
    }

    /**
     * 获取通知限制时长（分钟）      */
    private int getNotificationLimitDurationMinute() {
        return EnvConstants.notificationLimitDurationMinute > 0
                ? EnvConstants.notificationLimitDurationMinute
                : DEFAULT_NOTIFY_LIMIT_DURATION_MINUTE;
    }

    /**
     * 内存限流条目      */
    private static class MemoryLimitEntry {
        long count;
        final LocalDateTime timestamp;

        MemoryLimitEntry(long count, LocalDateTime timestamp) {
            this.count = count;
            this.timestamp = timestamp;
        }
    }
}
