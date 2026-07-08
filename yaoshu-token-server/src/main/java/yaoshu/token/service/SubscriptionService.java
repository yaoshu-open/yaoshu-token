package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.mapper.SubscriptionPreConsumeRecordMapper;
import yaoshu.token.mapper.UserSubscriptionMapper;
import yaoshu.token.pojo.entity.SubscriptionPlan;
import yaoshu.token.pojo.entity.SubscriptionPreConsumeRecord;
import yaoshu.token.pojo.entity.UserSubscription;

import java.util.List;

/**
 * 订阅计费服务  * <p>
 * 封装订阅预扣费、后结算差额调整、退款三个核心操作。
 * 所有操作使用 @Transactional 保证原子性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserSubscriptionMapper userSubscriptionMapper;
    private final SubscriptionPreConsumeRecordMapper preConsumeRecordMapper;
    private final SubscriptionPlanService subscriptionPlanService;

    /**
     * 订阅预扣费结果      */
    public static class PreConsumeResult {
        public int userSubscriptionId;
        public long preConsumed;
        public long amountTotal;
        public long amountUsedBefore;
        public long amountUsedAfter;
    }

    /**
     * 订阅计划信息      */
    public static class PlanInfo {
        public int planId;
        public String planTitle;
    }

    /**
     * 检查用户是否有活跃订阅      */
    public boolean hasActiveSubscription(int userId) {
        if (userId <= 0) return false;
        long now = System.currentTimeMillis() / 1000;
        Long count = userSubscriptionMapper.selectCount(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId)
                        .eq(UserSubscription::getStatus, "active")
                        .gt(UserSubscription::getEndTime, now)
        );
        return count != null && count > 0;
    }

    /**
     * 根据用户订阅 ID 获取订阅计划信息      */
    public PlanInfo getPlanInfo(int userSubscriptionId) {
        if (userSubscriptionId <= 0) return null;
        UserSubscription sub = userSubscriptionMapper.selectById(userSubscriptionId);
        if (sub == null) return null;
        Integer planId = sub.getPlanId();
        if (planId == null || planId <= 0) return null;
        PlanInfo info = new PlanInfo();
        info.planId = planId;
        SubscriptionPlan plan = subscriptionPlanService.getPlanById(planId);
        if (plan != null) {
            info.planTitle = plan.getTitle();
        }
        return info;
    }

    /**
     * 订阅预扣费      * <p>
     * 幂等：同一 requestId 重复调用返回已有记录。
     * 遍历用户所有活跃订阅，找到余额充足的进行预扣。
     *
     * @param requestId 请求 ID（幂等键）
     * @param userId    用户 ID
     * @param modelName 模型名
     * @param amount    预扣额度
     * @return 预扣结果
     */
    @Transactional(rollbackFor = Exception.class)
    public PreConsumeResult preConsume(String requestId, int userId, String modelName, long amount) {
        if (userId <= 0) {
            throw new RuntimeException("invalid userId");
        }
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new RuntimeException("requestId is empty");
        }
        if (amount <= 0) {
            throw new RuntimeException("amount must be > 0");
        }

        PreConsumeResult result = new PreConsumeResult();
        long now = System.currentTimeMillis() / 1000;

        // 幂等检查：同一 requestId 是否已有预扣记录
        SubscriptionPreConsumeRecord existing = preConsumeRecordMapper.selectOne(
                new LambdaQueryWrapper<SubscriptionPreConsumeRecord>()
                        .eq(SubscriptionPreConsumeRecord::getRequestId, requestId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            if ("refunded".equals(existing.getStatus())) {
                throw new RuntimeException("subscription pre-consume already refunded");
            }
            UserSubscription sub = userSubscriptionMapper.selectById(existing.getUserSubscriptionId());
            if (sub == null) {
                throw new RuntimeException("subscription not found");
            }
            result.userSubscriptionId = sub.getId();
            result.preConsumed = existing.getPreConsumed() != null ? existing.getPreConsumed() : 0;
            result.amountTotal = sub.getAmountTotal() != null ? sub.getAmountTotal() : 0;
            result.amountUsedBefore = sub.getAmountUsed() != null ? sub.getAmountUsed() : 0;
            result.amountUsedAfter = result.amountUsedBefore;
            return result;
        }

        // 查询用户所有活跃订阅（按到期时间升序，行锁防并发预扣 lost update）
        List<UserSubscription> subs = userSubscriptionMapper.selectList(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId)
                        .eq(UserSubscription::getStatus, "active")
                        .gt(UserSubscription::getEndTime, now)
                        .orderByAsc(UserSubscription::getEndTime)
                        .orderByAsc(UserSubscription::getId)
                        .last("FOR UPDATE")
        );
        if (subs.isEmpty()) {
            throw new RuntimeException("no active subscription");
        }

        // 遍历找到余额充足的订阅
        for (UserSubscription sub : subs) {
            // 周期到期按需重置（daily/weekly/monthly/custom）
            try {
                yaoshu.token.pojo.entity.SubscriptionPlan plan =
                        subscriptionPlanService.getPlanById(sub.getPlanId());
                subscriptionPlanService.maybeResetUserSubscriptionWithPlan(sub, plan, now);
            } catch (Exception e) {
                // plan 已被删除等异常场景：放弃重置但不阻塞预扣
                log.warn("subscription reset skipped subId={} planId={} err={}", sub.getId(), sub.getPlanId(), e.getMessage());
            }
            long usedBefore = sub.getAmountUsed() != null ? sub.getAmountUsed() : 0;
            long total = sub.getAmountTotal() != null ? sub.getAmountTotal() : 0;
            if (total > 0) {
                long remain = total - usedBefore;
                if (remain < amount) {
                    continue;
                }
            }

            // 创建预扣记录
            SubscriptionPreConsumeRecord record = new SubscriptionPreConsumeRecord();
            record.setRequestId(requestId);
            record.setUserId(userId);
            record.setUserSubscriptionId(sub.getId());
            record.setPreConsumed(amount);
            record.setStatus("consumed");
            record.setPlanId(sub.getPlanId() != null ? sub.getPlanId() : 0);
            record.setModel(modelName);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            try {
                preConsumeRecordMapper.insert(record);
            } catch (Exception e) {
                // 唯一索引冲突 → 并发场景，查已有记录
                SubscriptionPreConsumeRecord dup = preConsumeRecordMapper.selectOne(
                        new LambdaQueryWrapper<SubscriptionPreConsumeRecord>()
                                .eq(SubscriptionPreConsumeRecord::getRequestId, requestId)
                                .last("LIMIT 1")
                );
                if (dup != null) {
                    if ("refunded".equals(dup.getStatus())) {
                        throw new RuntimeException("subscription pre-consume already refunded");
                    }
                    result.userSubscriptionId = sub.getId();
                    result.preConsumed = dup.getPreConsumed() != null ? dup.getPreConsumed() : 0;
                    result.amountTotal = total;
                    result.amountUsedBefore = usedBefore;
                    result.amountUsedAfter = usedBefore;
                    return result;
                }
                throw new RuntimeException("failed to create pre-consume record", e);
            }

            // 更新订阅已用额度
            sub.setAmountUsed(usedBefore + amount);
            userSubscriptionMapper.updateById(sub);

            result.userSubscriptionId = sub.getId();
            result.preConsumed = amount;
            result.amountTotal = total;
            result.amountUsedBefore = usedBefore;
            result.amountUsedAfter = usedBefore + amount;
            return result;
        }

        throw new RuntimeException("subscription quota insufficient, need=" + amount);
    }

    /**
     * 订阅后结算差额调整      *
     * @param userSubscriptionId 订阅 ID
     * @param delta              差额（正数补扣，负数退还）
     */
    @Transactional(rollbackFor = Exception.class)
    public void postConsumeDelta(int userSubscriptionId, long delta) {
        if (userSubscriptionId <= 0) {
            throw new RuntimeException("invalid userSubscriptionId");
        }
        if (delta == 0) return;

        // 行锁防并发结算 lost update
        UserSubscription sub = userSubscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getId, userSubscriptionId)
                        .last("FOR UPDATE")
        );
        if (sub == null) {
            throw new RuntimeException("subscription not found: " + userSubscriptionId);
        }

        long currentUsed = sub.getAmountUsed() != null ? sub.getAmountUsed() : 0;
        long newUsed = currentUsed + delta;
        if (newUsed < 0) newUsed = 0;

        long total = sub.getAmountTotal() != null ? sub.getAmountTotal() : 0;
        if (total > 0 && newUsed > total) {
            throw new RuntimeException("subscription used exceeds total, used=" + newUsed + " total=" + total);
        }

        LambdaUpdateWrapper<UserSubscription> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserSubscription::getId, userSubscriptionId)
                .set(UserSubscription::getAmountUsed, newUsed);
        userSubscriptionMapper.update(null, uw);
    }

    /**
     * 退还订阅预扣费（幂等）      *
     * @param requestId 请求 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void refundPreConsume(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw new RuntimeException("requestId is empty");
        }

        // 行锁防并发退款
        SubscriptionPreConsumeRecord record = preConsumeRecordMapper.selectOne(
                new LambdaQueryWrapper<SubscriptionPreConsumeRecord>()
                        .eq(SubscriptionPreConsumeRecord::getRequestId, requestId)
                        .last("LIMIT 1 FOR UPDATE")
        );
        if (record == null) {
            throw new RuntimeException("pre-consume record not found for requestId: " + requestId);
        }

        if ("refunded".equals(record.getStatus())) {
            return; // 已退款，幂等返回
        }

        long preConsumed = record.getPreConsumed() != null ? record.getPreConsumed() : 0;
        if (preConsumed > 0) {
            postConsumeDelta(record.getUserSubscriptionId(), -preConsumed);
        }

        long now = System.currentTimeMillis() / 1000;
        LambdaUpdateWrapper<SubscriptionPreConsumeRecord> uw = new LambdaUpdateWrapper<>();
        uw.eq(SubscriptionPreConsumeRecord::getId, record.getId())
                .set(SubscriptionPreConsumeRecord::getStatus, "refunded")
                .set(SubscriptionPreConsumeRecord::getUpdatedAt, now);
        preConsumeRecordMapper.update(null, uw);
    }
}
