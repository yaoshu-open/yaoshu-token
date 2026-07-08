package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.mapper.TokenMapper;
import yaoshu.token.relay.common.BillingSettler;
import yaoshu.token.relay.common.RelayInfo;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 计费会话服务  * <p>
 * 封装单次请求的预扣费/结算/退款生命周期，实现 BillingSettler 接口。
 * 支持钱包（WalletFunding）和订阅（SubscriptionFunding）两种资金来源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingSessionService {

    private final UserService userService;
    private final TokenMapper tokenMapper;
    private final QuotaCalculator quotaCalculator;
    private final SubscriptionService subscriptionService;

    /**
     * 创建计费会话并执行预扣费（钱包路径）      *
     * @return 计费会话，或 null（表示配额不足）
     */
    public BillingSession newSession(RelayInfo relayInfo, int preConsumedQuota) {
        return newSession(relayInfo, preConsumedQuota, "wallet");
    }

    /**
     * 创建计费会话并执行预扣费，指定资金来源
     *
     * @param fundingSource "wallet" 或 "subscription"
     * @return 计费会话，或 null（表示配额不足）
     */
    public BillingSession newSession(RelayInfo relayInfo, int preConsumedQuota, String fundingSource) {
        FundingSource funding;
        if ("subscription".equals(fundingSource)) {
            long subAmount = preConsumedQuota;
            if (subAmount <= 0) subAmount = 1;
            funding = new SubscriptionFunding(
                    relayInfo.getRequestId(), relayInfo.getUserId(),
                    relayInfo.getOriginModelName(), subAmount, subscriptionService);
            // 标记使用订阅（供计费链路下游区分资金来源，如阶梯定价排除）
            if (relayInfo.getExtraData() == null) {
                relayInfo.setExtraData(new java.util.HashMap<>());
            }
            relayInfo.getExtraData().put("subscriptionUsed", true);
        } else {
            // 不够时直接返回 ErrorCodeInsufficientUserQuota，避免先扣 token 再回滚的冗余 IO
            long userQuota = userService.getUserQuota(relayInfo.getUserId());
            if (userQuota <= 0) {
                log.warn("用户额度不足: userId={}, 剩余额度={}", relayInfo.getUserId(), userQuota);
                return null;
            }
            if (userQuota - preConsumedQuota < 0) {
                log.warn("预扣费额度失败: userId={}, 剩余额度={}, 需要预扣费额度={}",
                        relayInfo.getUserId(), userQuota, preConsumedQuota);
                return null;
            }
            relayInfo.setUserQuota(userQuota);
            funding = new WalletFunding(relayInfo.getUserId(), userService);
        }

        BillingSession session = new BillingSession(relayInfo, preConsumedQuota,
                userService, tokenMapper, quotaCalculator, subscriptionService, funding);
        String error = session.preConsume();
        if (error != null) {
            log.warn("预扣费失败: {}", error);
            return null;
        }
        return session;
    }

    /**
     * 根据用户计费偏好创建会话      * <p>
     * 偏好策略：subscription_first（默认）/ wallet_first / subscription_only / wallet_only
     *
     * @return 计费会话，或 null（表示配额不足）
     */
    public BillingSession newSessionWithPreference(RelayInfo relayInfo, int preConsumedQuota, String preference) {
        String pref = preference == null ? "subscription_first" : preference.toLowerCase();

        switch (pref) {
            case "subscription_only":
                return newSession(relayInfo, preConsumedQuota, "subscription");
            case "wallet_only":
                return newSession(relayInfo, preConsumedQuota, "wallet");
            case "wallet_first": {
                BillingSession session = newSession(relayInfo, preConsumedQuota, "wallet");
                if (session == null) {
                    return newSession(relayInfo, preConsumedQuota, "subscription");
                }
                return session;
            }
            case "subscription_first":
            default: {
                // 先检查是否有活跃订阅
                if (subscriptionService.hasActiveSubscription(relayInfo.getUserId())) {
                    BillingSession session = newSession(relayInfo, preConsumedQuota, "subscription");
                    if (session == null) {
                        // 订阅额度不足，回退钱包
                        return newSession(relayInfo, preConsumedQuota, "wallet");
                    }
                    return session;
                }
                return newSession(relayInfo, preConsumedQuota, "wallet");
            }
        }
    }

    /**
     * 计费会话内部类，实现 BillingSettler 供 RelayInfo 引用
     */
    public static class BillingSession implements BillingSettler {

        private final RelayInfo relayInfo;
        private final UserService userService;
        private final TokenMapper tokenMapper;
        private final QuotaCalculator quotaCalculator;
        private final SubscriptionService subscriptionService;
        private final FundingSource funding;

        private int preConsumedQuota;
        private int tokenConsumed;
        private int extraReserved;
        private boolean trusted;
        private boolean fundingSettled;
        private boolean settled;
        private boolean refunded;
        private final ReentrantLock lock = new ReentrantLock();

        BillingSession(RelayInfo relayInfo, int preConsumedQuota,
                        UserService userService, TokenMapper tokenMapper,
                        QuotaCalculator quotaCalculator, SubscriptionService subscriptionService,
                        FundingSource funding) {
            this.relayInfo = relayInfo;
            this.preConsumedQuota = preConsumedQuota;
            this.userService = userService;
            this.tokenMapper = tokenMapper;
            this.quotaCalculator = quotaCalculator;
            this.subscriptionService = subscriptionService;
            this.funding = funding;
        }

        /**
         * 执行预扣费：信任检查 → 令牌预扣 → 资金来源预扣
         */
        String preConsume() {
            int userId = relayInfo.getUserId();
            int effective = preConsumedQuota;

            // 信任额度旁路
            int trustQuota = quotaCalculator.getTrustQuota();
            if (shouldTrust(trustQuota)) {
                trusted = true;
                effective = 0;
                log.info("用户 {} 额度充足, 信任且不需要预扣费 (funding={})", userId, funding.source());
            }

            // 1) 预扣令牌额度（Playground 临时 Token tokenId=0 跳过，直接走用户额度预扣）
            if (effective > 0 && relayInfo.getTokenId() > 0) {
                int dec = tokenMapper.decreaseRemainQuota(relayInfo.getTokenId(), effective);
                if (dec <= 0) {
                    return "预扣 Token 配额失败";
                }
                tokenConsumed = effective;
            }

            // 2) 预扣资金来源
            try {
                funding.preConsume(effective);
            } catch (Exception e) {
                // 预扣费失败，回滚令牌额度
                if (tokenConsumed > 0) {
                    tokenMapper.increaseRemainQuotaSafe(relayInfo.getTokenId(), tokenConsumed);
                    tokenConsumed = 0;
                }
                String msg = e.getMessage();
                if (msg != null && (msg.contains("no active subscription") || msg.contains("subscription quota insufficient"))) {
                    return "订阅额度不足或未配置订阅: " + msg;
                }
                return msg != null ? msg : "预扣费失败";
            }

            preConsumedQuota = effective;
            syncRelayInfo();
            return null;
        }

        /**
         * 将 BillingSession 状态同步到 RelayInfo 兼容字段          * <p>
         * 同步 BillingSource、订阅相关字段（供日志记录与异步任务消费）。
         */
        private void syncRelayInfo() {
            relayInfo.setFinalPreConsumedQuota(preConsumedQuota);
            relayInfo.setBillingSource(funding.source());

            if (funding instanceof SubscriptionFunding sf) {
                relayInfo.setSubscriptionId(sf.getSubscriptionId());
                relayInfo.setSubscriptionPreConsumed(sf.getPreConsumed() + extraReserved);
                relayInfo.setSubscriptionPostDelta(0);
                relayInfo.setSubscriptionAmountTotal(sf.getAmountTotal());
                relayInfo.setSubscriptionAmountUsedAfterPreConsume(sf.getAmountUsedAfter() + extraReserved);
                relayInfo.setSubscriptionPlanId(sf.getPlanId());
                relayInfo.setSubscriptionPlanTitle(sf.getPlanTitle());
            } else {
                relayInfo.setSubscriptionId(0);
                relayInfo.setSubscriptionPreConsumed(0);
            }
        }

        @Override
        public void settle(int actualQuota) {
            lock.lock();
            try {
                if (settled) return;
                int delta = actualQuota - preConsumedQuota;
                if (delta == 0) {
                    settled = true;
                    return;
                }

                // 1) 调整资金来源（仅在尚未提交时执行）
                if (!fundingSettled) {
                    try {
                        funding.settle(delta);
                    } catch (Exception e) {
                        log.error("资金来源结算失败 (funding={}): {}", funding.source(), e.getMessage());
                        return;
                    }
                    fundingSettled = true;
                }

                // 2) 调整令牌额度（delta>0 表示实际消耗多于预扣 → 多扣 token；delta<0 → 退还）
                // Playground 不操作令牌额度（临时 Token）
                if (!relayInfo.isPlayground()) {
                    if (delta > 0) {
                        tokenMapper.increaseUsedQuota(relayInfo.getTokenId(), delta);
                    } else if (delta < 0) {
                        // 实际消耗少于预扣，退还差额（绝对值）
                        tokenMapper.increaseRemainQuotaSafe(relayInfo.getTokenId(), -delta);
                    }
                }

                // 3) 订阅资金来源同步 SubscriptionPostDelta（用于日志）
                if ("subscription".equals(funding.source())) {
                    long current = relayInfo.getSubscriptionPostDelta();
                    relayInfo.setSubscriptionPostDelta(current + delta);
                }

                settled = true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void refund() {
            lock.lock();
            try {
                if (settled || refunded || !needsRefundLocked()) {
                    return;
                }
                refunded = true;
            } finally {
                lock.unlock();
            }

            log.info("用户 {} 请求失败, 返还预扣费 (token_quota={}, funding={})",
                    relayInfo.getUserId(), tokenConsumed, funding.source());

            // 1) 退还资金来源
            try {
                funding.refund();
            } catch (Exception e) {
                log.error("error refunding funding source: {}", e.getMessage());
            }

            // 1.5) 订阅 + 有额外 reserve 时单独回滚 extraReserved（Go billing_session.go L107-L113）
            if (extraReserved > 0 && "subscription".equals(funding.source())
                    && funding instanceof SubscriptionFunding sf && sf.getSubscriptionId() > 0) {
                try {
                    subscriptionService.postConsumeDelta(sf.getSubscriptionId(), -extraReserved);
                } catch (Exception e) {
                    log.error("error refunding subscription extra reserved quota: {}", e.getMessage());
                }
            }

            // 2) 退还令牌额度（Playground 不操作令牌）
            if (tokenConsumed > 0 && !relayInfo.isPlayground()) {
                tokenMapper.increaseRemainQuotaSafe(relayInfo.getTokenId(), tokenConsumed);
            }
        }

        @Override
        public boolean needsRefund() {
            lock.lock();
            try {
                return needsRefundLocked();
            } finally {
                lock.unlock();
            }
        }

        private boolean needsRefundLocked() {
            if (settled || refunded || fundingSettled) return false;
            if (tokenConsumed > 0) return true;
            // 订阅可能在 tokenConsumed=0 时仍预扣了额度（如 Playground 场景）
            if (funding instanceof SubscriptionFunding sf && sf.getPreConsumed() > 0) {
                return true;
            }
            return false;
        }

        @Override
        public int getPreConsumedQuota() { return preConsumedQuota; }

        /**
         * 发送前补充预扣额度（订阅场景需要）          */
        @Override
        public void reserve(int targetQuota) {
            lock.lock();
            try {
                if (settled || refunded || trusted || targetQuota <= preConsumedQuota) {
                    return;
                }
                int delta = targetQuota - preConsumedQuota;
                if (delta <= 0) return;

                // 预扣资金来源
                try {
                    if (funding.source().equals("wallet")) {
                        userService.decreaseUserQuota(relayInfo.getUserId(), delta);
                    } else if (funding.source().equals("subscription")) {
                        // 订阅通过 postConsumeDelta 追加
                        if (funding instanceof SubscriptionFunding sf) {
                            subscriptionService.postConsumeDelta(sf.getSubscriptionId(), delta);
                        }
                    }
                } catch (Exception e) {
                    log.error("reserve funding failed: {}", e.getMessage());
                    return;
                }

                // 预扣令牌额度
                int dec = tokenMapper.decreaseRemainQuota(relayInfo.getTokenId(), delta);
                if (dec <= 0) {
                    // 回滚资金来源（钱包/订阅都需回滚）
                    if (funding.source().equals("wallet")) {
                        try {
                            userService.increaseUserQuota(relayInfo.getUserId(), delta);
                        } catch (Exception e) {
                            log.error("error rolling back wallet funding reserve: {}", e.getMessage());
                        }
                    } else if (funding.source().equals("subscription")
                            && funding instanceof SubscriptionFunding sf && sf.getSubscriptionId() > 0) {
                        try {
                            subscriptionService.postConsumeDelta(sf.getSubscriptionId(), -delta);
                        } catch (Exception e) {
                            log.error("error rolling back subscription funding reserve: {}", e.getMessage());
                        }
                    }
                    return;
                }

                preConsumedQuota += delta;
                tokenConsumed += delta;
                extraReserved += delta;
                syncRelayInfo();
            } finally {
                lock.unlock();
            }
        }

        private boolean shouldTrust(int trustQuota) {
            // 异步任务（ForcePreConsume=true）必须预扣全额，不允许信任旁路
            if (relayInfo.isForcePreConsume()) return false;
            if (trustQuota <= 0) return false;
            // 订阅不启用信任旁路（与 Go shouldTrust 一致）
            if ("subscription".equals(funding.source())) return false;

            long userQuota = userService.getUserQuota(relayInfo.getUserId());
            if (userQuota <= trustQuota) return false;

            if (relayInfo.isTokenUnlimited()) return true;
            long tokenQuota = getTokenRemainQuota();
            return tokenQuota > trustQuota;
        }

        private long getTokenRemainQuota() {
            var token = tokenMapper.selectById(relayInfo.getTokenId());
            if (token == null || token.getRemainQuota() == null) return 0;
            if (Boolean.TRUE.equals(token.getUnlimitedQuota())) return Long.MAX_VALUE;
            return token.getRemainQuota();
        }
    }
}
