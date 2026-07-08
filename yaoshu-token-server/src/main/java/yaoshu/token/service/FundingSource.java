package yaoshu.token.service;

/**
 * 资金来源接口（钱包 or 订阅）  * <p>
 * 抽象预扣费的资金来源，由 WalletFunding 和 SubscriptionFunding 实现。
 */
public interface FundingSource {

    /** 返回资金来源标识："wallet" 或 "subscription" */
    String source();

    /** 从该资金来源预扣 amount 额度 */
    void preConsume(int amount);

    /** 根据差额调整资金来源（正数补扣，负数退还） */
    void settle(int delta);

    /** 退还所有预扣费 */
    void refund();
}
