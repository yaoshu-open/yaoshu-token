package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

/**
 * 钱包资金来源  * <p>
 * 直接操作用户 quota 字段（预扣/结算/退款）。
 */
@Slf4j
public class WalletFunding implements FundingSource {

    private final int userId;
    private final UserService userService;
    private int consumed;

    public WalletFunding(int userId, UserService userService) {
        this.userId = userId;
        this.userService = userService;
    }

    @Override
    public String source() {
        return "wallet";
    }

    @Override
    public void preConsume(int amount) {
        if (amount <= 0) return;
        userService.decreaseUserQuota(userId, amount);
        consumed = amount;
    }

    @Override
    public void settle(int delta) {
        if (delta == 0) return;
        if (delta > 0) {
            userService.decreaseUserQuota(userId, delta);
        } else {
            userService.increaseUserQuota(userId, -delta);
        }
    }

    @Override
    public void refund() {
        if (consumed <= 0) return;
        // IncreaseUserQuota 是 quota += N 的非幂等操作，不能重试
        userService.increaseUserQuota(userId, consumed);
    }

    public int getConsumed() {
        return consumed;
    }
}
