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
 * 1. 到期订阅判定：auto_renew 且套餐启用且余额充足 → 自动续期；否则标记 expired + 分组回退
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
    private final SubscriptionPlanService subscriptionPlanService;

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
     * 到期订阅判定：自动续期或标记过期
     * <p>
     * 将 end_time < now 且 status=active 的订阅逐条判定：
     * - auto_renew=true 且套餐启用且余额充足 → 自动续期（扣费+延长endTime+写order+保持active）
     * - 否则（auto_renew=false 或套餐下架或余额不足）→ 标记 expired + 分组回退
     * 每条订阅在独立事务内（subscriptionPlanService 的方法自带 @Transactional），避免单条失败影响其他订阅。
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
                try {
                    boolean autoRenew = sub.getAutoRenew() != null && sub.getAutoRenew();
                    if (autoRenew) {
                        // 尝试自动续期
                        boolean renewed = subscriptionPlanService.renewSubscriptionAutomatically(sub);
                        if (!renewed) {
                            // 余额不足/套餐下架，标记过期 + 分组回退
                            subscriptionPlanService.expireSubscriptionGracefully(sub);
                            log.info("subscription {} expired (auto_renew failed: insufficient balance or plan disabled), user={}",
                                    sub.getId(), sub.getUserId());
                        } else {
                            log.info("subscription {} auto-renewed, user={}", sub.getId(), sub.getUserId());
                        }
                    } else {
                        // 用户已关闭续期，标记过期 + 分组回退
                        subscriptionPlanService.expireSubscriptionGracefully(sub);
                        log.info("subscription {} expired (auto_renew=false), user={}", sub.getId(), sub.getUserId());
                    }
                } catch (Exception e) {
                    // 单条订阅处理失败不中断其他订阅
                    log.warn("subscription {} expire/renew failed: {}", sub.getId(), e.getMessage());
                }
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
