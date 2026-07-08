package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.util.I18nUtils;
import cn.hutool.v7.core.util.RandomUtil;
import cn.hutool.v7.core.text.StrUtil;
import yaoshu.token.common.StrUtilCompat;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.config.GeneralSettingConfig;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.mapper.RedemptionMapper;
import yaoshu.token.mapper.TopUpMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.pojo.entity.Log;
import yaoshu.token.pojo.entity.Redemption;
import yaoshu.token.pojo.entity.TopUp;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.vo.PayMethodVO;
import yaoshu.token.pojo.vo.TopupInfoVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 用户充值与兑换码服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopupService {

    private final OptionService optionService;
    private final UserService userService;
    private final TopUpMapper topUpMapper;
    private final RedemptionMapper redemptionMapper;
    private final UserMapper userMapper;
    private final LogMapper logMapper;

    private static final String PAYMENT_COMPLIANCE_KEY = "payment_setting.compliance_confirmed";
    private static final String PAYMENT_COMPLIANCE_TERMS_KEY = "payment_setting.compliance_terms_version";
    private static final String CURRENT_COMPLIANCE_TERMS_VERSION = "v1";
    private static final long TOPUP_QUERY_WINDOW_SECONDS = 30L * 24 * 60 * 60;
    private static final int LOG_TYPE_TOPUP = 1;
    private static final int REDEMPTION_STATUS_ENABLED = 1;
    private static final int REDEMPTION_STATUS_USED = 2;
    private static final String PAYMENT_PROVIDER_EPAY = "epay";
    private static final String PAYMENT_PROVIDER_WAFFO_PANCAKE = "waffo_pancake";

    private final ConcurrentHashMap<Integer, ReentrantLock> topupLocks = new ConcurrentHashMap<>();

    public TopupInfoVO getTopupInfo(String userGroup) {
        boolean complianceConfirmed = isPaymentComplianceConfirmed();
        List<PayMethodVO> payMethods = new ArrayList<>(complianceConfirmed ? getPayMethods() : Collections.emptyList());

        if (isStripeTopupEnabled() && !containsPayMethod(payMethods, "stripe")) {
            payMethods.add(buildMethod("Stripe", "stripe", "rgba(var(--semi-purple-5), 1)", getIntOption("StripeMinTopUp", 1)));
        }
        if (isWaffoPancakeTopupEnabled() && !containsPayMethod(payMethods, "waffo_pancake")) {
            payMethods.add(buildMethod("Waffo Pancake", "waffo_pancake", "rgba(var(--semi-orange-5), 1)",
                    getIntOption("WaffoPancakeMinTopUp", 1)));
        }
        if (isWaffoTopupEnabled() && !containsPayMethod(payMethods, "waffo")) {
            payMethods.add(buildMethod("Waffo (Global Payment)", "waffo", "rgba(var(--semi-blue-5), 1)",
                    getIntOption("WaffoMinTopUp", 1)));
        }

        return TopupInfoVO.builder()
                .enableOnlineTopup(isEpayTopupEnabled())
                .enableStripeTopup(isStripeTopupEnabled())
                .enableCreemTopup(isCreemTopupEnabled())
                .enableWaffoTopup(isWaffoTopupEnabled())
                .enableWaffoPancakeTopup(isWaffoPancakeTopupEnabled())
                .enableRedemption(complianceConfirmed)
                .paymentComplianceConfirmed(complianceConfirmed)
                .paymentComplianceTermsVersion(StrUtilCompat.blankToDefault(
                        optionService.getValue(PAYMENT_COMPLIANCE_TERMS_KEY), CURRENT_COMPLIANCE_TERMS_VERSION))
                .waffoPayMethods(parseJsonArrayOption("WaffoPayMethods"))
                .creemProducts(parseJsonArrayOption("CreemProducts"))
                .payMethods(payMethods)
                .minTopup(getMinTopup())
                .stripeMinTopup(getIntOption("StripeMinTopUp", 1))
                .waffoMinTopup(getIntOption("WaffoMinTopUp", 1))
                .waffoPancakeMinTopup(getIntOption("WaffoPancakeMinTopUp", 1))
                .amountOptions(List.of(10, 20, 50, 100, 200, 500))
                .discount(new LinkedHashMap<>())
                .topupLink("")
                .build();
    }

    public List<TopUp> listUserTopups(int userId, String keyword) {
        long cutoff = now() - TOPUP_QUERY_WINDOW_SECONDS;

        LambdaQueryWrapper<TopUp> wrapper = new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getUserId, userId)
                .ge(TopUp::getCreateTime, cutoff);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(TopUp::getTradeNo, keyword.trim());
        }
        wrapper.orderByDesc(TopUp::getId);
        return topUpMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public int redeem(String key, int userId) {
        if (StrUtil.isBlank(key)) {
            throw new RuntimeException(I18nUtils.get("redemption.not_provided"));
        }
        if (userId <= 0) {
            throw new RuntimeException(I18nUtils.get("common.invalid_id"));
        }
        ReentrantLock lock = topupLocks.computeIfAbsent(userId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new RuntimeException(I18nUtils.get("user.topup_processing"));
        }
        try {
            Redemption redemption = redemptionMapper.selectOne(
                    new LambdaQueryWrapper<Redemption>()
                            .eq(Redemption::getKey, key.trim())
                            .last("LIMIT 1 FOR UPDATE"));
            if (redemption == null) {
                throw new RuntimeException(I18nUtils.get("redemption.invalid"));
            }
            if (!Integer.valueOf(REDEMPTION_STATUS_ENABLED).equals(redemption.getStatus())) {
                throw new RuntimeException(I18nUtils.get("redemption.used"));
            }
            if (redemption.getExpiredTime() != null && redemption.getExpiredTime() != 0 && redemption.getExpiredTime() < now()) {
                throw new RuntimeException(I18nUtils.get("redemption.expired"));
            }
            int quota = redemption.getQuota() == null ? 0 : redemption.getQuota();
            userMapper.increaseQuota(userId, quota);
            redemption.setRedeemedTime(now());
            redemption.setStatus(REDEMPTION_STATUS_USED);
            redemption.setUsedUserId(userId);
            redemptionMapper.updateById(redemption);
            recordTopupLog(userId, "通过兑换码充值 " + quota + "，兑换码ID " + redemption.getId());
            return quota;
        } finally {
            lock.unlock();
        }
    }

    public BigDecimal calculatePayMoney(long amount, String group) {
        BigDecimal displayAmount = BigDecimal.valueOf(amount);
        if (GeneralSettingConfig.QUOTA_DISPLAY_TOKENS.equals(GeneralSettingConfig.getQuotaDisplayType())) {
            displayAmount = displayAmount.divide(BigDecimal.valueOf(CommonConstants.quotaPerUnit), 6, RoundingMode.HALF_UP);
        }
        BigDecimal topupGroupRatio = BigDecimal.valueOf(TopupRatioService.getRatio(StrUtilCompat.blankToDefault(group, "default")));
        BigDecimal price = BigDecimal.valueOf(getDoubleOption("Price", 7.3D));
        return displayAmount.multiply(price).multiply(topupGroupRatio);
    }

    public BigDecimal calculateGatewayPayMoney(long amount, String group, String unitPriceOptionKey) {
        BigDecimal displayAmount = BigDecimal.valueOf(amount);
        if (GeneralSettingConfig.QUOTA_DISPLAY_TOKENS.equals(GeneralSettingConfig.getQuotaDisplayType())) {
            displayAmount = displayAmount.divide(BigDecimal.valueOf(CommonConstants.quotaPerUnit), 6, RoundingMode.HALF_UP);
        }
        BigDecimal topupGroupRatio = BigDecimal.valueOf(TopupRatioService.getRatio(StrUtilCompat.blankToDefault(group, "default")));
        BigDecimal unitPrice = BigDecimal.valueOf(getDoubleOption(unitPriceOptionKey, 1D));
        return displayAmount.multiply(unitPrice).multiply(topupGroupRatio);
    }

    public long getMinTopup() {
        long minTopup = getIntOption("MinTopUp", 1);
        if (GeneralSettingConfig.QUOTA_DISPLAY_TOKENS.equals(GeneralSettingConfig.getQuotaDisplayType())) {
            minTopup = BigDecimal.valueOf(minTopup)
                    .multiply(BigDecimal.valueOf(CommonConstants.quotaPerUnit))
                    .longValue();
        }
        return minTopup;
    }

    public boolean isPaymentComplianceConfirmed() {
        return "true".equalsIgnoreCase(optionService.getValue(PAYMENT_COMPLIANCE_KEY))
                && CURRENT_COMPLIANCE_TERMS_VERSION.equals(
                StrUtilCompat.blankToDefault(optionService.getValue(PAYMENT_COMPLIANCE_TERMS_KEY), CURRENT_COMPLIANCE_TERMS_VERSION));
    }

    public boolean isStripeTopupEnabled() {
        return isPaymentComplianceConfirmed()
                && hasOption("StripeApiSecret")
                && hasOption("StripeWebhookSecret")
                && hasOption("StripePriceId");
    }

    public boolean isCreemTopupEnabled() {
        String products = optionService.getValue("CreemProducts");
        return isPaymentComplianceConfirmed()
                && hasOption("CreemApiKey")
                && StrUtil.isNotBlank(products)
                && !"[]".equals(products.trim());
    }

    public boolean isWaffoTopupEnabled() {
        if (!isPaymentComplianceConfirmed() || !"true".equalsIgnoreCase(optionService.getValue("WaffoEnabled"))) {
            return false;
        }
        boolean sandbox = "true".equalsIgnoreCase(optionService.getValue("WaffoSandbox"));
        if (sandbox) {
            return hasOption("WaffoSandboxApiKey") && hasOption("WaffoSandboxPrivateKey") && hasOption("WaffoSandboxPublicCert");
        }
        return hasOption("WaffoApiKey") && hasOption("WaffoPrivateKey") && hasOption("WaffoPublicCert");
    }

    public boolean isWaffoPancakeTopupEnabled() {
        return isPaymentComplianceConfirmed()
                && hasOption("WaffoPancakeMerchantID")
                && hasOption("WaffoPancakePrivateKey")
                && hasOption("WaffoPancakeProductID");
    }

    public boolean isEpayTopupEnabled() {
        return isPaymentComplianceConfirmed()
                && hasOption("PayAddress")
                && hasOption("EpayId")
                && hasOption("EpayKey")
                && !getPayMethods().isEmpty();
    }

    public boolean containsPayMethod(String paymentMethod) {
        return containsPayMethod(getPayMethods(), StrUtilCompat.blankToDefault(paymentMethod, "").trim());
    }

    @Transactional(rollbackFor = Exception.class)
    public TopUp createPendingEpayOrder(int userId, long amount, String paymentMethod) {
        if (userId <= 0) {
            throw new RuntimeException(I18nUtils.get("common.invalid_id"));
        }
        if (amount < getMinTopup()) {
            throw new RuntimeException(I18nUtils.get("topup.invalid_quota")
                    + "，最小充值额度 " + getMinTopup());
        }
        if (!containsPayMethod(paymentMethod)) {
            throw new RuntimeException(I18nUtils.get("payment.method_not_exists"));
        }
        String group = userService.getUserGroup(userId);
        BigDecimal payMoney = calculatePayMoney(amount, group);
        if (payMoney.compareTo(new BigDecimal("0.01")) < 0) {
            throw new RuntimeException(I18nUtils.get("payment.amount_too_low"));
        }

        TopUp topUp = new TopUp();
        topUp.setUserId(userId);
        topUp.setAmount(normalizeStoredAmount(amount));
        topUp.setMoney(payMoney.setScale(2, RoundingMode.HALF_UP).doubleValue());
        topUp.setTradeNo(String.format("USR%dNO%s%d", userId, RandomUtil.randomLettersAndNumbers(6), now()));
        topUp.setPaymentMethod(paymentMethod);
        topUp.setPaymentProvider(PAYMENT_PROVIDER_EPAY);
        topUp.setCreateTime(now());
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_PENDING);
        topUpMapper.insert(topUp);
        return topUp;
    }

    @Transactional(rollbackFor = Exception.class)
    public TopUp createPendingGatewayOrder(int userId,
                                           long amount,
                                           double payMoney,
                                           String paymentMethod,
                                           String paymentProvider,
                                           String tradeNo) {
        return createPendingGatewayOrderInternal(userId, normalizeStoredAmount(amount), payMoney, paymentMethod, paymentProvider, tradeNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public TopUp createPendingFixedQuotaOrder(int userId,
                                              long amount,
                                              double payMoney,
                                              String paymentMethod,
                                              String paymentProvider,
                                              String tradeNo) {
        return createPendingGatewayOrderInternal(userId, amount, payMoney, paymentMethod, paymentProvider, tradeNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeEpayOrder(String tradeNo, String actualPaymentMethod) {
        if (StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException(I18nUtils.get("topup.not_provided"));
        }
        TopUp topUp = topUpMapper.selectOne(new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, tradeNo)
                .last("LIMIT 1 FOR UPDATE"));
        if (topUp == null) {
            throw new RuntimeException(I18nUtils.get("topup.order_not_exists"));
        }
        if (!PAYMENT_PROVIDER_EPAY.equals(topUp.getPaymentProvider())) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        if (CommonConstants.TOP_UP_STATUS_SUCCESS.equals(topUp.getStatus())) {
            return;
        }
        if (!CommonConstants.TOP_UP_STATUS_PENDING.equals(topUp.getStatus())) {
            throw new RuntimeException(I18nUtils.get("topup.order_status"));
        }
        if (StrUtil.isNotBlank(actualPaymentMethod) && !StrUtil.equals(topUp.getPaymentMethod(), actualPaymentMethod)) {
            topUp.setPaymentMethod(actualPaymentMethod);
        }
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
        topUp.setCompleteTime(now());
        topUpMapper.updateById(topUp);

        int quotaToAdd = BigDecimal.valueOf(topUp.getAmount() == null ? 0L : topUp.getAmount())
                .multiply(BigDecimal.valueOf(CommonConstants.quotaPerUnit))
                .setScale(0, RoundingMode.DOWN)
                .intValue();
        if (quotaToAdd > 0) {
            userMapper.increaseQuota(topUp.getUserId(), quotaToAdd);
        }
        recordTopupLog(topUp.getUserId(),
                String.format("使用在线充值成功，充值额度: %d，支付金额：%.2f", quotaToAdd, topUp.getMoney()));
    }

    /**
     * 完成 Waffo Pancake 充值订单（对齐 Go model.RechargeWaffoPancake）。
     * <p>
     * 事务内 SELECT FOR UPDATE 行锁 + status 幂等校验 + quota 累加 + 写日志。
     * Go 原版无显式 LockOrder，靠行锁保证 tradeNo 幂等。
     *
     * @param tradeNo 本地订单号（webhook 中 event.data.orderMerchantExternalId）
     */
    @Transactional(rollbackFor = Exception.class)
    public void rechargeWaffoPancake(String tradeNo) {
        if (StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException("未提供支付单号");
        }
        TopUp topUp = topUpMapper.selectOne(new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, tradeNo)
                .last("LIMIT 1 FOR UPDATE"));
        if (topUp == null) {
            throw new RuntimeException("充值订单不存在");
        }
        if (!PAYMENT_PROVIDER_WAFFO_PANCAKE.equals(topUp.getPaymentProvider())) {
            throw new RuntimeException("支付方式不匹配");
        }
        // 幂等：已成功直接返回（重复 webhook 不重复累加配额）
        if (CommonConstants.TOP_UP_STATUS_SUCCESS.equals(topUp.getStatus())) {
            return;
        }
        if (!CommonConstants.TOP_UP_STATUS_PENDING.equals(topUp.getStatus())) {
            throw new RuntimeException("充值订单状态错误");
        }
        int quotaToAdd = BigDecimal.valueOf(topUp.getAmount() == null ? 0L : topUp.getAmount())
                .multiply(BigDecimal.valueOf(CommonConstants.quotaPerUnit))
                .setScale(0, RoundingMode.DOWN)
                .intValue();
        if (quotaToAdd <= 0) {
            throw new RuntimeException("无效的充值额度");
        }
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
        topUp.setCompleteTime(now());
        topUpMapper.updateById(topUp);
        userMapper.increaseQuota(topUp.getUserId(), quotaToAdd);
        recordTopupLog(topUp.getUserId(),
                String.format("Waffo Pancake充值成功，充值额度: %d，支付金额: %.2f", quotaToAdd, topUp.getMoney()));
    }

    /**
     * 标记订单为 failed（拉起支付失败时的状态回退，对齐 Go topUp.Status = TopUpStatusFailed）。
     * <p>
     * 仅用于订单创建后但支付链路拉起失败的场景（如上游 API 调用失败）。
     * 静默失败（找不到订单不报错，避免遮蔽原始异常）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markTopupFailed(String tradeNo) {
        if (StrUtil.isBlank(tradeNo)) {
            return;
        }
        TopUp topUp = topUpMapper.selectOne(new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, tradeNo)
                .last("LIMIT 1"));
        if (topUp == null || !CommonConstants.TOP_UP_STATUS_PENDING.equals(topUp.getStatus())) {
            return;
        }
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_FAILED);
        topUpMapper.updateById(topUp);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeStripeOrder(String tradeNo, String customerId, String callerIp) {
        if (StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException(I18nUtils.get("topup.not_provided"));
        }
        TopUp topUp = topUpMapper.selectOne(new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, tradeNo)
                .last("LIMIT 1 FOR UPDATE"));
        if (topUp == null) {
            throw new RuntimeException(I18nUtils.get("topup.order_not_exists"));
        }
        if (!"stripe".equals(topUp.getPaymentProvider())) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        if (CommonConstants.TOP_UP_STATUS_SUCCESS.equals(topUp.getStatus())) {
            return;
        }
        if (!CommonConstants.TOP_UP_STATUS_PENDING.equals(topUp.getStatus())) {
            throw new RuntimeException(I18nUtils.get("topup.order_status"));
        }
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
        topUp.setCompleteTime(now());
        topUpMapper.updateById(topUp);

        int quotaToAdd = BigDecimal.valueOf(topUp.getMoney() == null ? 0D : topUp.getMoney())
                .multiply(BigDecimal.valueOf(CommonConstants.quotaPerUnit))
                .setScale(0, RoundingMode.DOWN)
                .intValue();
        if (quotaToAdd > 0) {
            userMapper.increaseQuota(topUp.getUserId(), quotaToAdd);
        }
        if (StrUtil.isNotBlank(customerId)) {
            User user = userMapper.selectById(topUp.getUserId());
            if (user != null && !StrUtil.equals(user.getStripeCustomer(), customerId)) {
                user.setStripeCustomer(customerId);
                userMapper.updateById(user);
            }
        }
        recordTopupLog(topUp.getUserId(),
                String.format("使用在线充值成功，充值额度: %d，支付金额：%.2f", quotaToAdd, topUp.getMoney()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeCreemOrder(String tradeNo, String customerEmail, String customerName, String callerIp) {
        if (StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException(I18nUtils.get("topup.not_provided"));
        }
        TopUp topUp = topUpMapper.selectOne(new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, tradeNo)
                .last("LIMIT 1 FOR UPDATE"));
        if (topUp == null) {
            throw new RuntimeException(I18nUtils.get("topup.order_not_exists"));
        }
        if (!"creem".equals(topUp.getPaymentProvider())) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        if (CommonConstants.TOP_UP_STATUS_SUCCESS.equals(topUp.getStatus())) {
            return;
        }
        if (!CommonConstants.TOP_UP_STATUS_PENDING.equals(topUp.getStatus())) {
            throw new RuntimeException(I18nUtils.get("topup.order_status"));
        }
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
        topUp.setCompleteTime(now());
        topUpMapper.updateById(topUp);

        int quotaToAdd = BigDecimal.valueOf(topUp.getAmount() == null ? 0L : topUp.getAmount())
                .setScale(0, RoundingMode.DOWN)
                .intValue();
        if (quotaToAdd > 0) {
            userMapper.increaseQuota(topUp.getUserId(), quotaToAdd);
        }
        if (StrUtil.isNotBlank(customerEmail)) {
            User user = userMapper.selectById(topUp.getUserId());
            if (user != null && StrUtil.isBlank(user.getEmail())) {
                user.setEmail(customerEmail);
                userMapper.updateById(user);
            }
        }
        recordTopupLog(topUp.getUserId(),
                String.format("使用Creem充值成功，充值额度: %d，支付金额：%.2f", quotaToAdd, topUp.getMoney()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeWaffoOrder(String tradeNo, String callerIp) {
        if (StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException(I18nUtils.get("topup.not_provided"));
        }
        TopUp topUp = topUpMapper.selectOne(new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, tradeNo)
                .last("LIMIT 1 FOR UPDATE"));
        if (topUp == null) {
            throw new RuntimeException(I18nUtils.get("topup.order_not_exists"));
        }
        if (!"waffo".equals(topUp.getPaymentProvider())) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        if (CommonConstants.TOP_UP_STATUS_SUCCESS.equals(topUp.getStatus())) {
            return;
        }
        if (!CommonConstants.TOP_UP_STATUS_PENDING.equals(topUp.getStatus())) {
            throw new RuntimeException(I18nUtils.get("topup.order_status"));
        }
        int quotaToAdd = BigDecimal.valueOf(topUp.getAmount() == null ? 0L : topUp.getAmount())
                .multiply(BigDecimal.valueOf(CommonConstants.quotaPerUnit))
                .setScale(0, RoundingMode.DOWN)
                .intValue();
        if (quotaToAdd <= 0) {
            throw new RuntimeException(I18nUtils.get("topup.quota_invalid"));
        }
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
        topUp.setCompleteTime(now());
        topUpMapper.updateById(topUp);
        userMapper.increaseQuota(topUp.getUserId(), quotaToAdd);
        recordTopupLog(topUp.getUserId(),
                String.format("Waffo充值成功，充值额度: %d，支付金额：%.2f", quotaToAdd, topUp.getMoney()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePendingGatewayOrderStatus(String tradeNo, String expectedPaymentProvider, String targetStatus) {
        if (StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException(I18nUtils.get("topup.not_provided"));
        }
        TopUp topUp = topUpMapper.selectOne(new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, tradeNo)
                .last("LIMIT 1 FOR UPDATE"));
        if (topUp == null) {
            return;
        }
        if (StrUtil.isNotBlank(expectedPaymentProvider)
                && !StrUtil.equals(topUp.getPaymentProvider(), expectedPaymentProvider)) {
            throw new RuntimeException(I18nUtils.get("common.invalid_params"));
        }
        if (!CommonConstants.TOP_UP_STATUS_PENDING.equals(topUp.getStatus())) {
            return;
        }
        topUp.setStatus(targetStatus);
        topUpMapper.updateById(topUp);
    }

    public long normalizeGatewayStoredAmount(long amount) {
        return normalizeStoredAmount(amount);
    }

    public int getIntSetting(String key, int defaultValue) {
        return getIntOption(key, defaultValue);
    }

    private TopUp createPendingGatewayOrderInternal(int userId,
                                                    long storedAmount,
                                                    double payMoney,
                                                    String paymentMethod,
                                                    String paymentProvider,
                                                    String tradeNo) {
        if (userId <= 0) {
            throw new RuntimeException(I18nUtils.get("subscription.invalid_user_id"));
        }
        if (storedAmount <= 0) {
            throw new RuntimeException(I18nUtils.get("topup.quota_less_than_one"));
        }
        if (StrUtil.isBlank(paymentMethod) || StrUtil.isBlank(paymentProvider) || StrUtil.isBlank(tradeNo)) {
            throw new RuntimeException(I18nUtils.get("topup.payment_params_error"));
        }
        BigDecimal money = BigDecimal.valueOf(payMoney).setScale(2, RoundingMode.HALF_UP);
        if (money.compareTo(new BigDecimal("0.01")) < 0) {
            throw new RuntimeException(I18nUtils.get("topup.amount_too_low"));
        }
        TopUp topUp = new TopUp();
        topUp.setUserId(userId);
        topUp.setAmount(storedAmount);
        topUp.setMoney(money.doubleValue());
        topUp.setTradeNo(tradeNo);
        topUp.setPaymentMethod(paymentMethod);
        topUp.setPaymentProvider(paymentProvider);
        topUp.setCreateTime(now());
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_PENDING);
        topUpMapper.insert(topUp);
        return topUp;
    }

    private List<PayMethodVO> getPayMethods() {
        String raw = optionService.getValue("PayMethods");
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        return Convert.toJSONArray(raw).toJavaList(PayMethodVO.class);
    }

    private boolean containsPayMethod(List<PayMethodVO> payMethods, String type) {
        for (PayMethodVO method : payMethods) {
            if (type.equals(method.getType())) {
                return true;
            }
        }
        return false;
    }

    private PayMethodVO buildMethod(String name, String type, String color, int minTopup) {
        return PayMethodVO.builder()
                .name(name)
                .type(type)
                .color(color)
                .minTopup(String.valueOf(minTopup))
                .build();
    }

    private List<Object> parseJsonArrayOption(String key) {
        String raw = optionService.getValue(key);
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        com.alibaba.fastjson2.JSONArray array = com.alibaba.fastjson2.JSON.parseArray(raw);
        return new ArrayList<>(array);
    }

    private boolean hasOption(String key) {
        return StrUtil.isNotBlank(optionService.getValue(key));
    }

    private int getIntOption(String key, int defaultValue) {
        try {
            return Integer.parseInt(StrUtilCompat.blankToDefault(optionService.getValue(key), String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long normalizeStoredAmount(long amount) {
        if (!GeneralSettingConfig.QUOTA_DISPLAY_TOKENS.equals(GeneralSettingConfig.getQuotaDisplayType())) {
            return amount;
        }
        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(CommonConstants.quotaPerUnit), 0, RoundingMode.DOWN)
                .longValue();
    }

    private double getDoubleOption(String key, double defaultValue) {
        try {
            return Double.parseDouble(StrUtilCompat.blankToDefault(optionService.getValue(key), String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 管理员手动完成充值订单并给用户充值。      * 流程：FOR UPDATE 行锁 → 状态校验(pending) → Stripe/非Stripe 额度计算 → 标记完成 → 用户额度增加 → 记录日志
     *
     * @param tradeNo  支付单号
     * @param callerIp 操作者 IP（用于日志记录）
     * @return true=补单成功
     */
    @Transactional
    public boolean manualCompleteTopUp(String tradeNo, String callerIp) {
        if (tradeNo == null || tradeNo.isEmpty()) {
            log.warn("ManualCompleteTopUp: tradeNo 为空");
            return false;
        }

        // FOR UPDATE 行锁，避免并发补单
        LambdaQueryWrapper<TopUp> lockWrapper = new LambdaQueryWrapper<TopUp>()
                .eq(TopUp::getTradeNo, tradeNo)
                .last("FOR UPDATE");
        TopUp topUp = topUpMapper.selectOne(lockWrapper);
        if (topUp == null) {
            log.warn("ManualCompleteTopUp: 充值订单不存在 tradeNo={}", tradeNo);
            return false;
        }

        // 幂等处理：已成功直接返回
        if (CommonConstants.TOP_UP_STATUS_SUCCESS.equals(topUp.getStatus())) {
            return true;
        }

        if (!CommonConstants.TOP_UP_STATUS_PENDING.equals(topUp.getStatus())) {
            log.warn("ManualCompleteTopUp: 订单状态不是待支付 tradeNo={} status={}", tradeNo, topUp.getStatus());
            return false;
        }

        // 计算应充值额度：
        // - Stripe 订单：Money 代表经分组倍率换算后的美元数量，直接 * quotaPerUnit
        // - 其他订单（如易支付）：Amount 为配额数量，直接使用
        int quotaToAdd;
        if ("stripe".equals(topUp.getPaymentProvider())) {
            BigDecimal dMoney = BigDecimal.valueOf(topUp.getMoney() != null ? topUp.getMoney() : 0);
            BigDecimal dQuotaPerUnit = BigDecimal.valueOf(CommonConstants.quotaPerUnit);
            quotaToAdd = dMoney.multiply(dQuotaPerUnit).intValue();
        } else {
            // 其他支付：amount 字段是配额数量
            quotaToAdd = topUp.getAmount() != null ? topUp.getAmount().intValue() : 0;
        }
        if (quotaToAdd <= 0) {
            log.warn("ManualCompleteTopUp: 无效的充值额度 tradeNo={}", tradeNo);
            return false;
        }

        // 标记完成
        topUp.setStatus(CommonConstants.TOP_UP_STATUS_SUCCESS);
        topUp.setCompleteTime(now());
        topUpMapper.updateById(topUp);

        // 增加用户额度
        userMapper.increaseQuota(topUp.getUserId(), quotaToAdd);

        // 事务外记录日志（Go: RecordTopupLog）
        double payMoney = topUp.getMoney() != null ? topUp.getMoney() : 0;
        recordTopupLog(topUp.getUserId(),
                "管理员补单成功，充值额度: " + quotaToAdd + "，支付金额: " + payMoney);
        return true;
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

    private long now() {
        return System.currentTimeMillis() / 1000;
    }
}
