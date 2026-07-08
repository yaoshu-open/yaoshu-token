package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

/**
 * 订阅资金来源  * <p>
 * 通过 SubscriptionService 操作订阅额度（预扣/结算/退款）。
 * 退款使用 requestId 幂等保护，可安全重试。
 */
@Slf4j
public class SubscriptionFunding implements FundingSource {

    private final String requestId;
    private final int userId;
    private final String modelName;
    private final long amount;

    private final SubscriptionService subscriptionService;

    // PreConsume 成功后填充
    private int subscriptionId;
    private long preConsumed;
    private long amountTotal;
    private long amountUsedAfter;
    private int planId;
    private String planTitle;

    public SubscriptionFunding(String requestId, int userId, String modelName, long amount,
                                SubscriptionService subscriptionService) {
        this.requestId = requestId;
        this.userId = userId;
        this.modelName = modelName;
        this.amount = amount;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public String source() {
        return "subscription";
    }

    @Override
    public void preConsume(int ignored) {
        // amount 参数被忽略，使用构造时的 amount
        SubscriptionService.PreConsumeResult res = subscriptionService.preConsume(requestId, userId, modelName, amount);
        this.subscriptionId = res.userSubscriptionId;
        this.preConsumed = res.preConsumed;
        this.amountTotal = res.amountTotal;
        this.amountUsedAfter = res.amountUsedAfter;
        // 获取订阅计划信息
        SubscriptionService.PlanInfo planInfo = subscriptionService.getPlanInfo(res.userSubscriptionId);
        if (planInfo != null) {
            this.planId = planInfo.planId;
            this.planTitle = planInfo.planTitle;
        }
    }

    @Override
    public void settle(int delta) {
        if (delta == 0) return;
        subscriptionService.postConsumeDelta(subscriptionId, delta);
    }

    @Override
    public void refund() {
        if (preConsumed <= 0) return;
        // 订阅退款基于 requestId 幂等，可重试
        int maxAttempts = 3;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                subscriptionService.refundPreConsume(requestId);
                return;
            } catch (Exception e) {
                log.warn("subscription refund attempt {} failed: {}", i + 1, e.getMessage());
                if (i < maxAttempts - 1) {
                    try {
                        Thread.sleep(200L * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    public int getSubscriptionId() { return subscriptionId; }
    public long getPreConsumed() { return preConsumed; }
    public long getAmountTotal() { return amountTotal; }
    public long getAmountUsedAfter() { return amountUsedAfter; }
    public int getPlanId() { return planId; }
    public String getPlanTitle() { return planTitle; }
}
