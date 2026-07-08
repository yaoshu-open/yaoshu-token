package yaoshu.token.relay.common;

/**
 * 抽象计费会话的生命周期操作  * <p>
 * 由 service.BillingSession 实现，存储在 RelayInfo 上以避免循环引用。
 */
public interface BillingSettler {

    /**
     * 根据实际消耗额度进行结算，计算 delta = actualQuota - preConsumedQuota，
     * 同时调整资金来源（钱包/订阅）和令牌额度。
     */
    void settle(int actualQuota) throws Exception;

    /**
     * 退还所有预扣费额度（资金来源 + 令牌），幂等安全。
     * 通过线程池异步执行。如果已经结算或退款则不做任何操作。
     */
    void refund();

    /**
     * 返回会话是否存在需要退还的预扣状态（未结算且未退款）。
     */
    boolean needsRefund();

    /**
     * 返回实际预扣的额度值（信任用户可能为 0）。
     */
    int getPreConsumedQuota();

    /**
     * 将预扣额度补到目标值；若目标值不高于当前预扣额度则不做任何事。
     */
    void reserve(int targetQuota) throws Exception;
}
