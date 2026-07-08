package yaoshu.token.service;

import cn.hutool.v7.core.util.RandomUtil;
import cn.hutool.v7.core.text.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import ai.yue.library.base.util.I18nUtils;
import lombok.RequiredArgsConstructor;
import ai.yue.library.base.util.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.config.ratio.GroupRatioConfig;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.mapper.SubscriptionOrderMapper;
import yaoshu.token.mapper.SubscriptionPlanMapper;
import yaoshu.token.mapper.TopUpMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.mapper.UserSubscriptionMapper;
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.pojo.entity.SubscriptionOrder;
import yaoshu.token.pojo.entity.SubscriptionPlan;
import yaoshu.token.pojo.entity.TopUp;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.entity.UserSubscription;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 订阅计划与订单业务服务  * <p>
 * 与 {@link SubscriptionService}（运行时预扣费/结算）职责区分：本服务负责套餐 CRUD、
 * 余额购买、管理员绑定、订单完成/过期，以及用户订阅的查询/作废/删除与分组升降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanMapper planMapper;
    private final SubscriptionOrderMapper orderMapper;
    private final TopUpMapper topUpMapper;
    private final UserSubscriptionMapper userSubscriptionMapper;
    private final UserMapper userMapper;
    private final LogMapper logMapper;

    // ======================== 时长单位常量
    public static final String DURATION_YEAR = "year";
    public static final String DURATION_MONTH = "month";
    public static final String DURATION_DAY = "day";
    public static final String DURATION_HOUR = "hour";
    public static final String DURATION_CUSTOM = "custom";

    // ======================== 重置周期常量
    public static final String RESET_NEVER = "never";
    public static final String RESET_DAILY = "daily";
    public static final String RESET_WEEKLY = "weekly";
    public static final String RESET_MONTHLY = "monthly";
    public static final String RESET_CUSTOM = "custom";

    /** 日志类型：充值*/
    private static final int LOG_TYPE_TOPUP = 1;

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    // ======================== 计划查询 ========================

    /** 获取计划 */
    public SubscriptionPlan getPlanById(int id) {
        if (id <= 0) {
            throw new RuntimeException("invalid plan id");
        }
        SubscriptionPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new RuntimeException(I18nUtils.get("subscription.not_exists"));
        }
        normalizeDefaults(plan);
        return plan;
    }

    /** 列出全部计划（管理端）*/
    public List<SubscriptionPlan> listAllPlans() {
        List<SubscriptionPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<SubscriptionPlan>()
                        .orderByDesc(SubscriptionPlan::getSortOrder)
                        .orderByDesc(SubscriptionPlan::getId));
        plans.forEach(SubscriptionPlanService::normalizeDefaults);
        return plans;
    }

    /** 列出启用计划（用户端）*/
    public List<SubscriptionPlan> listEnabledPlans() {
        List<SubscriptionPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<SubscriptionPlan>()
                        .eq(SubscriptionPlan::getEnabled, true)
                        .orderByDesc(SubscriptionPlan::getSortOrder)
                        .orderByDesc(SubscriptionPlan::getId));
        plans.forEach(SubscriptionPlanService::normalizeDefaults);
        return plans;
    }

    /** 计划默认值兜底*/
    private static void normalizeDefaults(SubscriptionPlan plan) {
        if (plan.getAllowBalancePay() == null) {
            plan.setAllowBalancePay(true);
        }
    }

    /** 归一化重置周期 */
    public static String normalizeResetPeriod(String period) {
        String p = period == null ? "" : period.trim();
        switch (p) {
            case RESET_DAILY:
            case RESET_WEEKLY:
            case RESET_MONTHLY:
            case RESET_CUSTOM:
                return p;
            default:
                return RESET_NEVER;
        }
    }

    // ======================== 计划 CRUD（管理端） ========================

    @Transactional(rollbackFor = Exception.class)
    public SubscriptionPlan createPlan(SubscriptionPlan plan) {
        preparePlanForSave(plan, true);
        long now = now();
        plan.setId(null);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        planMapper.insert(plan);
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(int id, SubscriptionPlan plan) {
        if (id <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.invalid_id"));
        }
        SubscriptionPlan existed = planMapper.selectById(id);
        if (existed == null) {
            throw new RuntimeException(I18nUtils.get("subscription.not_exists"));
        }
        plan.setId(id);
        preparePlanForSave(plan, false);
        plan.setCreatedAt(existed.getCreatedAt());
        plan.setUpdatedAt(now());
        planMapper.updateById(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePlanStatus(int id, boolean enabled) {
        if (id <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.invalid_id"));
        }
        LambdaUpdateWrapper<SubscriptionPlan> uw = new LambdaUpdateWrapper<>();
        uw.eq(SubscriptionPlan::getId, id)
                .set(SubscriptionPlan::getEnabled, enabled)
                .set(SubscriptionPlan::getUpdatedAt, now());
        if (planMapper.update(null, uw) <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.not_exists"));
        }
    }

    private void preparePlanForSave(SubscriptionPlan plan, boolean creating) {
        if (plan == null) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        normalizeDefaults(plan);
        String title = plan.getTitle() == null ? "" : plan.getTitle().trim();
        if (title.isEmpty()) {
            throw new RuntimeException(I18nUtils.get("subscription.title_empty"));
        }
        if (plan.getPriceAmount() == null || plan.getPriceAmount() < 0) {
            throw new RuntimeException(I18nUtils.get("subscription.price_negative"));
        }
        if (plan.getPriceAmount() > 9999) {
            throw new RuntimeException(I18nUtils.get("subscription.price_max"));
        }
        if (plan.getMaxPurchasePerUser() != null && plan.getMaxPurchasePerUser() < 0) {
            throw new RuntimeException(I18nUtils.get("subscription.purchase_limit_negative"));
        }
        if (plan.getTotalAmount() != null && plan.getTotalAmount() < 0) {
            throw new RuntimeException(I18nUtils.get("subscription.quota_negative"));
        }
        String durationUnit = plan.getDurationUnit() == null ? "" : plan.getDurationUnit().trim();
        if (durationUnit.isEmpty()) {
            durationUnit = DURATION_MONTH;
        }
        plan.setDurationUnit(durationUnit);
        if (!DURATION_CUSTOM.equals(durationUnit)) {
            if (plan.getDurationValue() == null || plan.getDurationValue() <= 0) {
                plan.setDurationValue(1);
            }
        } else if (plan.getCustomSeconds() == null || plan.getCustomSeconds() <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.custom_duration_gt_zero"));
        }
        String upgradeGroup = plan.getUpgradeGroup() == null ? "" : plan.getUpgradeGroup().trim();
        if (!upgradeGroup.isEmpty() && !GroupRatioConfig.containsGroupRatio(upgradeGroup)) {
            throw new RuntimeException(I18nUtils.get("subscription.group_not_exists"));
        }
        String resetPeriod = normalizeResetPeriod(plan.getQuotaResetPeriod());
        if (RESET_CUSTOM.equals(resetPeriod)
                && (plan.getQuotaResetCustomSeconds() == null || plan.getQuotaResetCustomSeconds() <= 0)) {
            throw new RuntimeException(I18nUtils.get("subscription.reset_cycle_gt_zero"));
        }
        plan.setTitle(title);
        plan.setCurrency("USD");
        plan.setUpgradeGroup(upgradeGroup);
        plan.setQuotaResetPeriod(resetPeriod);
        if (plan.getAllowBalancePay() == null) {
            plan.setAllowBalancePay(true);
        }
        if (plan.getEnabled() == null) {
            plan.setEnabled(true);
        }
        if (plan.getSortOrder() == null) {
            plan.setSortOrder(0);
        }
        if (plan.getDurationValue() == null) {
            plan.setDurationValue(0);
        }
        if (plan.getCustomSeconds() == null) {
            plan.setCustomSeconds(0L);
        }
        if (plan.getMaxPurchasePerUser() == null) {
            plan.setMaxPurchasePerUser(0);
        }
        if (plan.getTotalAmount() == null) {
            plan.setTotalAmount(0L);
        }
        if (plan.getQuotaResetCustomSeconds() == null) {
            plan.setQuotaResetCustomSeconds(0L);
        }
        if (creating) {
            plan.setId(null);
        }
    }

    // ======================== 用户订阅查询 ========================

    public List<UserSubscription> listAllUserSubscriptions(int userId) {
        if (userId <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.invalid_user_id"));
        }
        return userSubscriptionMapper.selectList(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId)
                        .orderByDesc(UserSubscription::getEndTime)
                        .orderByDesc(UserSubscription::getId));
    }

    public List<UserSubscription> listActiveUserSubscriptions(int userId) {
        if (userId <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.invalid_user_id"));
        }
        long now = now();
        return userSubscriptionMapper.selectList(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId)
                        .eq(UserSubscription::getStatus, "active")
                        .gt(UserSubscription::getEndTime, now)
                        .orderByDesc(UserSubscription::getEndTime)
                        .orderByDesc(UserSubscription::getId));
    }

    public long countUserSubscriptionsByPlan(int userId, int planId) {
        if (userId <= 0 || planId <= 0) {
            throw new RuntimeException("invalid userId or planId");
        }
        return userSubscriptionMapper.selectCount(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId)
                        .eq(UserSubscription::getPlanId, planId));
    }

    // ======================== 余额购买 / 管理绑定 ========================

    @Transactional(rollbackFor = Exception.class)
    public void purchaseWithBalance(int userId, int planId) {
        if (userId <= 0 || planId <= 0) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        SubscriptionPlan plan = lockPlan(planId);
        if (!Boolean.TRUE.equals(plan.getEnabled())) {
            throw new RuntimeException(I18nUtils.get("subscription.plan_not_enabled"));
        }
        if (plan.getPriceAmount() == null || plan.getPriceAmount() < 0) {
            throw new RuntimeException(I18nUtils.get("subscription.price_negative"));
        }
        if (!Boolean.TRUE.equals(plan.getAllowBalancePay())) {
            throw new RuntimeException(I18nUtils.get("subscription.balance_not_allowed"));
        }
        int requiredQuota = calcSubscriptionBalanceQuota(plan.getPriceAmount());
        User user = lockUser(userId);
        long currentQuota = user.getQuota() == null ? 0 : user.getQuota();
        if (requiredQuota > 0 && currentQuota < requiredQuota) {
            throw new RuntimeException(I18nUtils.get("quota.insufficient"));
        }
        if (requiredQuota > 0) {
            userMapper.decreaseQuota(userId, requiredQuota);
        }
        createUserSubscriptionFromPlanTx(userId, plan, "balance");
        SubscriptionOrder order = new SubscriptionOrder();
        long now = now();
        order.setUserId(userId);
        order.setPlanId(plan.getId());
        order.setMoney(plan.getPriceAmount());
        order.setTradeNo(String.format("SUBBALUSR%dNO%s%d", userId, RandomUtil.randomLettersAndNumbers(6), System.nanoTime()));
        order.setPaymentMethod("balance");
        order.setPaymentProvider("balance");
        order.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
        order.setCreateTime(now);
        order.setCompleteTime(now);
        order.setProviderPayload("charged_quota=" + requiredQuota);
        orderMapper.insert(order);
        recordTopupLog(userId, String.format("使用余额购买订阅成功，套餐: %s，支付金额: %.2f，扣除额度: %d",
                plan.getTitle(), plan.getPriceAmount(), requiredQuota));
    }

    @Transactional(rollbackFor = Exception.class)
    public SubscriptionOrder createPendingEpayOrder(int userId, int planId, String paymentMethod) {
        if (userId <= 0 || planId <= 0) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        SubscriptionPlan plan = lockPlan(planId);
        if (!Boolean.TRUE.equals(plan.getEnabled())) {
            throw new RuntimeException(I18nUtils.get("subscription.plan_not_enabled"));
        }
        if (plan.getPriceAmount() == null || plan.getPriceAmount() < 0.01D) {
            throw new RuntimeException(I18nUtils.get("subscription.amount_too_low"));
        }
        if (plan.getMaxPurchasePerUser() != null && plan.getMaxPurchasePerUser() > 0) {
            long count = countUserSubscriptionsByPlan(userId, planId);
            if (count >= plan.getMaxPurchasePerUser()) {
                throw new RuntimeException(I18nUtils.get("subscription.purchase_limit_reached"));
            }
        }
        SubscriptionOrder order = new SubscriptionOrder();
        order.setUserId(userId);
        order.setPlanId(planId);
        order.setMoney(plan.getPriceAmount());
        order.setTradeNo(String.format("SUBUSR%dNO%s%d", userId, RandomUtil.randomLettersAndNumbers(6), now()));
        order.setPaymentMethod(paymentMethod);
        order.setPaymentProvider("epay");
        order.setCreateTime(now());
        order.setStatus(CommonConstants.TOP_UP_STATUS_PENDING);
        orderMapper.insert(order);
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public SubscriptionOrder createPendingExternalOrder(int userId,
                                                        int planId,
                                                        String paymentMethod,
                                                        String paymentProvider,
                                                        String tradeNo) {
        if (userId <= 0 || planId <= 0) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        if (StrUtil.isBlank(paymentMethod) || StrUtil.isBlank(paymentProvider) || StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException(I18nUtils.get("topup.payment_params_error"));
        }
        SubscriptionPlan plan = lockPlan(planId);
        if (!Boolean.TRUE.equals(plan.getEnabled())) {
            throw new RuntimeException(I18nUtils.get("subscription.plan_not_enabled"));
        }
        if (plan.getPriceAmount() == null || plan.getPriceAmount() < 0.01D) {
            throw new RuntimeException(I18nUtils.get("subscription.amount_too_low"));
        }
        if (plan.getMaxPurchasePerUser() != null && plan.getMaxPurchasePerUser() > 0) {
            long count = countUserSubscriptionsByPlan(userId, planId);
            if (count >= plan.getMaxPurchasePerUser()) {
                throw new RuntimeException(I18nUtils.get("subscription.purchase_limit_reached"));
            }
        }
        SubscriptionOrder order = new SubscriptionOrder();
        order.setUserId(userId);
        order.setPlanId(planId);
        order.setMoney(plan.getPriceAmount());
        order.setTradeNo(tradeNo);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentProvider(paymentProvider);
        order.setCreateTime(now());
        order.setStatus(CommonConstants.TOP_UP_STATUS_PENDING);
        orderMapper.insert(order);
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeExternalOrder(String tradeNo,
                                      String providerPayload,
                                      String expectedPaymentProvider,
                                      String actualPaymentMethod) {
        if (StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException("tradeNo is empty");
        }
        SubscriptionOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SubscriptionOrder>()
                .eq(SubscriptionOrder::getTradeNo, tradeNo)
                .last("LIMIT 1 FOR UPDATE"));
        if (order == null) {
            throw new RuntimeException(I18nUtils.get("subscription.not_found"));
        }
        if (StrUtil.isNotBlank(expectedPaymentProvider)
                && !StrUtil.equals(order.getPaymentProvider(), expectedPaymentProvider)) {
            throw new RuntimeException(I18nUtils.get("subscription.payment_gateway_mismatch"));
        }
        if (CommonConstants.TOP_UP_STATUS_SUCCESS.equals(order.getStatus())) {
            return;
        }
        if (!CommonConstants.TOP_UP_STATUS_PENDING.equals(order.getStatus())) {
            throw new RuntimeException(I18nUtils.get("subscription.order_status_error"));
        }

        SubscriptionPlan plan = getPlanById(order.getPlanId());
        createUserSubscriptionFromPlanTx(order.getUserId(), plan, "order");
        upsertSubscriptionTopUpTx(order);

        order.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
        order.setCompleteTime(now());
        if (StrUtil.isNotBlank(providerPayload)) {
            order.setProviderPayload(providerPayload);
        }
        if (StrUtil.isNotBlank(actualPaymentMethod) && !StrUtil.equals(order.getPaymentMethod(), actualPaymentMethod)) {
            order.setPaymentMethod(actualPaymentMethod);
        }
        orderMapper.updateById(order);
        recordTopupLog(order.getUserId(),
                String.format("订阅购买成功，套餐: %s，支付金额: %.2f，支付方式: %s",
                        plan.getTitle(), order.getMoney(), order.getPaymentMethod()));
    }

    public boolean tryCompleteExternalOrder(String tradeNo,
                                            String providerPayload,
                                            String expectedPaymentProvider,
                                            String actualPaymentMethod) {
        SubscriptionOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SubscriptionOrder>()
                .eq(SubscriptionOrder::getTradeNo, tradeNo)
                .last("LIMIT 1"));
        if (order == null) {
            return false;
        }
        completeExternalOrder(tradeNo, providerPayload, expectedPaymentProvider, actualPaymentMethod);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void expireOrder(String tradeNo, String expectedPaymentProvider) {
        if (StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException("tradeNo is empty");
        }
        SubscriptionOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SubscriptionOrder>()
                .eq(SubscriptionOrder::getTradeNo, tradeNo)
                .last("LIMIT 1 FOR UPDATE"));
        if (order == null) {
            throw new RuntimeException(I18nUtils.get("subscription.not_found"));
        }
        if (StrUtil.isNotBlank(expectedPaymentProvider)
                && !StrUtil.equals(order.getPaymentProvider(), expectedPaymentProvider)) {
            throw new RuntimeException(I18nUtils.get("subscription.payment_gateway_mismatch"));
        }
        if (!CommonConstants.TOP_UP_STATUS_PENDING.equals(order.getStatus())) {
            return;
        }
        order.setStatus(CommonConstants.TOP_UP_STATUS_EXPIRED);
        order.setCompleteTime(now());
        orderMapper.updateById(order);
    }

    public boolean tryExpireOrder(String tradeNo, String expectedPaymentProvider) {
        SubscriptionOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SubscriptionOrder>()
                .eq(SubscriptionOrder::getTradeNo, tradeNo)
                .last("LIMIT 1"));
        if (order == null) {
            return false;
        }
        expireOrder(tradeNo, expectedPaymentProvider);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public String adminBindSubscription(int userId, int planId) {
        if (userId <= 0 || planId <= 0) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        SubscriptionPlan plan = getPlanById(planId);
        createUserSubscriptionFromPlanTx(userId, plan, "admin");
        if (plan.getUpgradeGroup() != null && !plan.getUpgradeGroup().trim().isEmpty()) {
            return "用户分组将升级到 " + plan.getUpgradeGroup().trim();
        }
        return "";
    }

    @Transactional(rollbackFor = Exception.class)
    public String adminInvalidateUserSubscription(int userSubscriptionId) {
        if (userSubscriptionId <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.invalid_id"));
        }
        long now = now();
        UserSubscription sub = lockUserSubscription(userSubscriptionId);
        LambdaUpdateWrapper<UserSubscription> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserSubscription::getId, userSubscriptionId)
                .set(UserSubscription::getStatus, "cancelled")
                .set(UserSubscription::getEndTime, now)
                .set(UserSubscription::getUpdatedAt, now);
        userSubscriptionMapper.update(null, uw);
        String target = downgradeUserGroupIfNeeded(sub, now);
        return target.isEmpty() ? "" : "用户分组将回退到 " + target;
    }

    @Transactional(rollbackFor = Exception.class)
    public String adminDeleteUserSubscription(int userSubscriptionId) {
        if (userSubscriptionId <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.invalid_id"));
        }
        long now = now();
        UserSubscription sub = lockUserSubscription(userSubscriptionId);
        String target = downgradeUserGroupIfNeeded(sub, now);
        userSubscriptionMapper.deleteById(userSubscriptionId);
        return target.isEmpty() ? "" : "用户分组将回退到 " + target;
    }

    // ======================== 内部辅助 ========================

    private SubscriptionPlan lockPlan(int planId) {
        SubscriptionPlan plan = planMapper.selectOne(
                new LambdaQueryWrapper<SubscriptionPlan>()
                        .eq(SubscriptionPlan::getId, planId)
                        .last("LIMIT 1 FOR UPDATE"));
        if (plan == null) {
            throw new RuntimeException(I18nUtils.get("subscription.not_exists"));
        }
        normalizeDefaults(plan);
        return plan;
    }

    private User lockUser(int userId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getId, userId)
                        .last("LIMIT 1 FOR UPDATE"));
        if (user == null) {
            throw new RuntimeException(I18nUtils.get("admin.user_not_exists"));
        }
        return user;
    }

    private UserSubscription lockUserSubscription(int userSubscriptionId) {
        UserSubscription sub = userSubscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getId, userSubscriptionId)
                        .last("LIMIT 1 FOR UPDATE"));
        if (sub == null) {
            throw new RuntimeException(I18nUtils.get("subscription.not_found"));
        }
        return sub;
    }

    private int calcSubscriptionBalanceQuota(Double priceAmount) {
        if (priceAmount == null || priceAmount <= 0) {
            return 0;
        }
        if (CommonConstants.quotaPerUnit <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.quota_unit_config_error"));
        }
        return BigDecimal.valueOf(priceAmount)
                .multiply(BigDecimal.valueOf(CommonConstants.quotaPerUnit))
                .setScale(0, java.math.RoundingMode.CEILING)
                .intValue();
    }

    private void upsertSubscriptionTopUpTx(SubscriptionOrder order) {
        TopUp topUp = topUpMapper.selectOne(new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, order.getTradeNo())
                .last("LIMIT 1 FOR UPDATE"));
        long now = now();
        if (topUp == null) {
            TopUp created = new TopUp();
            created.setUserId(order.getUserId());
            created.setAmount(0L);
            created.setMoney(order.getMoney());
            created.setTradeNo(order.getTradeNo());
            created.setPaymentMethod(order.getPaymentMethod());
            created.setPaymentProvider(order.getPaymentProvider());
            created.setCreateTime(order.getCreateTime());
            created.setCompleteTime(now);
            created.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
            topUpMapper.insert(created);
            return;
        }
        topUp.setMoney(order.getMoney());
        if (StrUtil.isBlank(topUp.getPaymentMethod())) {
            topUp.setPaymentMethod(order.getPaymentMethod());
        } else if (!StrUtil.equals(topUp.getPaymentMethod(), order.getPaymentMethod())) {
            throw new RuntimeException(I18nUtils.get("subscription.payment_method_mismatch"));
        }
        if (StrUtil.isBlank(topUp.getPaymentProvider())) {
            topUp.setPaymentProvider(order.getPaymentProvider());
        } else if (!StrUtil.equals(topUp.getPaymentProvider(), order.getPaymentProvider())) {
            throw new RuntimeException(I18nUtils.get("subscription.payment_gateway_mismatch"));
        }
        if (topUp.getCreateTime() == null || topUp.getCreateTime() == 0) {
            topUp.setCreateTime(order.getCreateTime());
        }
        topUp.setCompleteTime(now);
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
        topUpMapper.updateById(topUp);
    }

    private UserSubscription createUserSubscriptionFromPlanTx(int userId, SubscriptionPlan plan, String source) {
        if (plan == null || plan.getId() == null || plan.getId() <= 0) {
            throw new RuntimeException("invalid plan");
        }
        if (plan.getMaxPurchasePerUser() != null && plan.getMaxPurchasePerUser() > 0) {
            long count = countUserSubscriptionsByPlan(userId, plan.getId());
            if (count >= plan.getMaxPurchasePerUser()) {
                throw new RuntimeException(I18nUtils.get("subscription.purchase_limit_reached"));
            }
        }
        User user = lockUser(userId);
        long startUnix = now();
        long endUnix = calcPlanEndTime(startUnix, plan);
        long nextReset = calcNextResetTime(startUnix, plan, endUnix);
        long lastReset = nextReset > 0 ? startUnix : 0;
        String upgradeGroup = plan.getUpgradeGroup() == null ? "" : plan.getUpgradeGroup().trim();
        String prevGroup = "";
        if (!upgradeGroup.isEmpty()) {
            String currentGroup = user.getGroup() == null ? "" : user.getGroup().trim();
            if (!upgradeGroup.equals(currentGroup)) {
                prevGroup = currentGroup;
                user.setGroup(upgradeGroup);
                userMapper.updateById(user);
            }
        }
        UserSubscription sub = new UserSubscription();
        sub.setUserId(userId);
        sub.setPlanId(plan.getId());
        sub.setAmountTotal(plan.getTotalAmount() == null ? 0L : plan.getTotalAmount());
        sub.setAmountUsed(0L);
        sub.setStartTime(startUnix);
        sub.setEndTime(endUnix);
        sub.setStatus("active");
        sub.setSource(source);
        sub.setLastResetTime(lastReset);
        sub.setNextResetTime(nextReset);
        sub.setUpgradeGroup(upgradeGroup);
        sub.setPrevUserGroup(prevGroup);
        sub.setCreatedAt(startUnix);
        sub.setUpdatedAt(startUnix);
        userSubscriptionMapper.insert(sub);
        return sub;
    }

    private long calcPlanEndTime(long startUnix, SubscriptionPlan plan) {
        LocalDateTime start = LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(startUnix), ZoneId.systemDefault());
        String durationUnit = plan.getDurationUnit() == null ? "" : plan.getDurationUnit().trim();
        Integer durationValue = plan.getDurationValue() == null ? 0 : plan.getDurationValue();
        switch (durationUnit) {
            case DURATION_YEAR:
                return start.plusYears(durationValue).atZone(ZoneId.systemDefault()).toEpochSecond();
            case DURATION_MONTH:
                return start.plusMonths(durationValue).atZone(ZoneId.systemDefault()).toEpochSecond();
            case DURATION_DAY:
                return start.plusDays(durationValue).atZone(ZoneId.systemDefault()).toEpochSecond();
            case DURATION_HOUR:
                return start.plusHours(durationValue).atZone(ZoneId.systemDefault()).toEpochSecond();
            case DURATION_CUSTOM:
                if (plan.getCustomSeconds() == null || plan.getCustomSeconds() <= 0) {
                    throw new RuntimeException("custom_seconds must be > 0");
                }
                return start.plusSeconds(plan.getCustomSeconds()).atZone(ZoneId.systemDefault()).toEpochSecond();
            default:
                throw new RuntimeException("invalid duration_unit: " + durationUnit);
        }
    }

    private long calcNextResetTime(long baseUnix, SubscriptionPlan plan, long endUnix) {
        String period = normalizeResetPeriod(plan.getQuotaResetPeriod());
        if (RESET_NEVER.equals(period)) {
            return 0;
        }
        LocalDateTime base = LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(baseUnix), ZoneId.systemDefault());
        LocalDateTime next;
        switch (period) {
            case RESET_DAILY:
                next = base.toLocalDate().plusDays(1).atStartOfDay();
                break;
            case RESET_WEEKLY:
                next = base.toLocalDate().with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atStartOfDay();
                break;
            case RESET_MONTHLY:
                next = base.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay();
                break;
            case RESET_CUSTOM:
                if (plan.getQuotaResetCustomSeconds() == null || plan.getQuotaResetCustomSeconds() <= 0) {
                    return 0;
                }
                next = base.plusSeconds(plan.getQuotaResetCustomSeconds());
                break;
            default:
                return 0;
        }
        long nextUnix = next.atZone(ZoneId.systemDefault()).toEpochSecond();
        return endUnix > 0 && nextUnix > endUnix ? 0 : nextUnix;
    }

    private String downgradeUserGroupIfNeeded(UserSubscription sub, long now) {
        if (sub == null) {
            return "";
        }
        String upgradeGroup = sub.getUpgradeGroup() == null ? "" : sub.getUpgradeGroup().trim();
        if (upgradeGroup.isEmpty()) {
            return "";
        }
        User user = lockUser(sub.getUserId());
        String currentGroup = user.getGroup() == null ? "" : user.getGroup().trim();
        if (!upgradeGroup.equals(currentGroup)) {
            return "";
        }
        UserSubscription otherActive = userSubscriptionMapper.selectOne(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, sub.getUserId())
                        .eq(UserSubscription::getStatus, "active")
                        .gt(UserSubscription::getEndTime, now)
                        .ne(UserSubscription::getId, sub.getId())
                        .isNotNull(UserSubscription::getUpgradeGroup)
                        .ne(UserSubscription::getUpgradeGroup, "")
                        .orderByDesc(UserSubscription::getEndTime)
                        .orderByDesc(UserSubscription::getId)
                        .last("LIMIT 1"));
        if (otherActive != null) {
            return "";
        }
        String prevGroup = sub.getPrevUserGroup() == null ? "" : sub.getPrevUserGroup().trim();
        if (prevGroup.isEmpty() || prevGroup.equals(currentGroup)) {
            return "";
        }
        user.setGroup(prevGroup);
        userMapper.updateById(user);
        return prevGroup;
    }

    private void recordTopupLog(int userId, String content) {
        User user = userMapper.selectById(userId);
        Log logEntry = new Log();
        logEntry.setUserId(userId);
        logEntry.setUsername(user == null ? null : user.getUsername());
        logEntry.setCreatedAt(now());
        logEntry.setType(LOG_TYPE_TOPUP);
        logEntry.setContent(content);
        logMapper.insert(logEntry);
    }

    /**
     * 周期到期时按需重置订阅 amountUsed      * <p>
     * 若 sub.nextResetTime 未到，直接返回 false；否则推进 lastResetTime/nextResetTime 并把
     * amountUsed 归零。仅在 sub 真正发生过周期推进（advanced=true）时才把 amountUsed 设为 0；
     * 首次初始化（lastResetTime=0 且 nextResetTime=0）只补齐 nextResetTime，不清零。
     * <p>
     * 调用方需在外层事务中传入已通过 FOR UPDATE 锁定的 sub 对象，本方法仅负责字段调整 + updateById。
     *
     * @return true 表示发生了 amountUsed 清零（advanced），调用方应使用最新 amountUsed=0；
     *         false 表示未发生重置（sub 字段可能仍被回写，比如首次初始化 nextResetTime）。
     */
    public boolean maybeResetUserSubscriptionWithPlan(UserSubscription sub, SubscriptionPlan plan, long now) {
        if (sub == null || plan == null) {
            throw new RuntimeException("invalid reset args");
        }
        if (sub.getNextResetTime() != null && sub.getNextResetTime() > 0 && sub.getNextResetTime() > now) {
            return false;
        }
        if (RESET_NEVER.equals(normalizeResetPeriod(plan.getQuotaResetPeriod()))) {
            return false;
        }
        long baseUnix = sub.getLastResetTime() == null ? 0L : sub.getLastResetTime();
        if (baseUnix <= 0) {
            baseUnix = sub.getStartTime() == null ? 0L : sub.getStartTime();
        }
        long endUnix = sub.getEndTime() == null ? 0L : sub.getEndTime();
        long next = calcNextResetTime(baseUnix, plan, endUnix);
        boolean advanced = false;
        while (next > 0 && next <= now) {
            advanced = true;
            baseUnix = next;
            next = calcNextResetTime(baseUnix, plan, endUnix);
        }
        if (!advanced) {
            // 首次初始化：lastResetTime/nextResetTime 都未设置时，仅补齐 nextResetTime
            long currentNext = sub.getNextResetTime() == null ? 0L : sub.getNextResetTime();
            if (currentNext == 0 && next > 0) {
                sub.setNextResetTime(next);
                sub.setLastResetTime(baseUnix);
                userSubscriptionMapper.updateById(sub);
            }
            return false;
        }
        sub.setAmountUsed(0L);
        sub.setLastResetTime(baseUnix);
        sub.setNextResetTime(next);
        userSubscriptionMapper.updateById(sub);
        return true;
    }
}
