package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ai.yue.library.data.redis.client.Redis;
import yaoshu.token.mapper.SubscriptionPreConsumeRecordMapper;
import yaoshu.token.mapper.UserSubscriptionMapper;
import yaoshu.token.pojo.entity.SubscriptionPreConsumeRecord;
import yaoshu.token.pojo.entity.UserSubscription;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 订阅额度重置定时任务  * <p>
 * 每分钟检查一次：
 * 1. 过期订阅 → status=expired
 * 2. 到期重置订阅 → amount_used=0, last_reset_time=now, next_reset_time += period
 * 3. 每 30 分钟清理 7 天前的预扣记录
 * <p>
 * 使用 AtomicBoolean 防止任务重入。  */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionResetTaskService {

    private final UserSubscriptionMapper userSubscriptionMapper;
    private final SubscriptionPreConsumeRecordMapper preConsumeRecordMapper;
    private final Redis redis;

    /** 每批处理数量*/
    private static final int BATCH_SIZE = 300;

    /** 清理间隔（分钟）*/
    private static final int CLEANUP_INTERVAL_MINUTES = 30;

    /** 预扣记录保留天数 */
    private static final int PRE_CONSUME_RECORD_RETENTION_DAYS = 7;

    /** Redis key：上次清理时间戳 */
    private static final String CLEANUP_LAST_KEY = "subscription:cleanup:last";

    /** 防重入标志 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 订阅维护定时任务      * <p>
     * 每 1 分钟执行一次      */
    @Scheduled(fixedDelay = 60_000)
    public void runSubscriptionQuotaReset() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            int totalExpired = expireDueSubscriptions();
            int totalReset = resetDueSubscriptions();
            cleanupOldPreConsumeRecords();

            if (totalReset > 0 || totalExpired > 0) {
                log.info("subscription maintenance: reset_count={}, expired_count={}", totalReset, totalExpired);
            }
        } catch (Exception e) {
            log.warn("subscription quota reset task failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /**
     * 过期订阅      * <p>
     * 将 end_time < now 且 status=active 的订阅标记为 expired
     *
     * @return 本次处理的数量
     */
    private int expireDueSubscriptions() {
        int total = 0;
        while (true) {
            long now = System.currentTimeMillis() / 1000;
            // 查询到期的活跃订阅
            var subs = userSubscriptionMapper.selectList(
                    new LambdaQueryWrapper<UserSubscription>()
                            .eq(UserSubscription::getStatus, "active")
                            .lt(UserSubscription::getEndTime, now)
                            .orderByAsc(UserSubscription::getId)
                            .last("LIMIT " + BATCH_SIZE)
            );
            if (subs.isEmpty()) {
                break;
            }
            for (UserSubscription sub : subs) {
                LambdaUpdateWrapper<UserSubscription> uw = new LambdaUpdateWrapper<>();
                uw.eq(UserSubscription::getId, sub.getId())
                        .eq(UserSubscription::getStatus, "active")
                        .set(UserSubscription::getStatus, "expired");
                userSubscriptionMapper.update(null, uw);
            }
            total += subs.size();
            if (subs.size() < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    /**
     * 重置到期订阅额度      * <p>
     * next_reset_time < now 的活跃订阅：amount_used=0, last_reset_time=now, next_reset_time += period
     *
     * @return 本次重置的数量
     */
    private int resetDueSubscriptions() {
        int total = 0;
        while (true) {
            long now = System.currentTimeMillis() / 1000;
            var subs = userSubscriptionMapper.selectList(
                    new LambdaQueryWrapper<UserSubscription>()
                            .eq(UserSubscription::getStatus, "active")
                            .isNotNull(UserSubscription::getNextResetTime)
                            .lt(UserSubscription::getNextResetTime, now)
                            .orderByAsc(UserSubscription::getId)
                            .last("LIMIT " + BATCH_SIZE)
            );
            if (subs.isEmpty()) {
                break;
            }
            for (UserSubscription sub : subs) {
                // 计算下次重置时间：基于上次重置时间 + 订阅周期
                Long lastReset = sub.getLastResetTime() != null ? sub.getLastResetTime() : sub.getStartTime();
                Long nextReset = sub.getNextResetTime();
                if (nextReset == null) nextReset = now;

                // 推算订阅周期（秒）：next_reset_time - last_reset_time
                long period = nextReset - (lastReset != null ? lastReset : now);
                if (period <= 0) period = 30 * 24 * 3600L; // 默认 30 天

                // 推进 next_reset_time 到未来
                long newNextReset = nextReset;
                while (newNextReset <= now) {
                    newNextReset += period;
                }

                LambdaUpdateWrapper<UserSubscription> uw = new LambdaUpdateWrapper<>();
                uw.eq(UserSubscription::getId, sub.getId())
                        .set(UserSubscription::getAmountUsed, 0L)
                        .set(UserSubscription::getLastResetTime, now)
                        .set(UserSubscription::getNextResetTime, newNextReset);
                userSubscriptionMapper.update(null, uw);
            }
            total += subs.size();
            if (subs.size() < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    /**
     * 清理过期的预扣记录      * <p>
     * 每 30 分钟执行一次，删除 7 天前的已结算/已退款记录
     */
    private void cleanupOldPreConsumeRecords() {
        // 检查上次清理时间（Redis 防多实例重复清理）
        Object lastObj = redis.get(CLEANUP_LAST_KEY);
        long lastCleanup = 0;
        if (lastObj instanceof Number) {
            lastCleanup = ((Number) lastObj).longValue();
        }
        long now = System.currentTimeMillis() / 1000;
        if (now - lastCleanup < CLEANUP_INTERVAL_MINUTES * 60L) {
            return;
        }

        long cutoff = now - (long) PRE_CONSUME_RECORD_RETENTION_DAYS * 24 * 3600;
        try {
            int deleted = preConsumeRecordMapper.delete(
                    new LambdaQueryWrapper<SubscriptionPreConsumeRecord>()
                            .lt(SubscriptionPreConsumeRecord::getCreatedAt, cutoff)
                            .in(SubscriptionPreConsumeRecord::getStatus, "consumed", "refunded")
            );
            if (deleted > 0) {
                log.info("cleaned up {} old pre-consume records (older than {}d)", deleted, PRE_CONSUME_RECORD_RETENTION_DAYS);
            }
            redis.set(CLEANUP_LAST_KEY, now, Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("cleanup pre-consume records failed: {}", e.getMessage());
        }
    }
}
