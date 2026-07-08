package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.pojo.dto.ErrorCode;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.handler.CompatibleHandler;

import java.util.List;

/**
 * 计费编排服务  * <p>
 * 核心流程：PreConsumeBilling（预扣费）→ SettleBilling（后结算，含新旧路径兼容）
 * 支持钱包和订阅两种资金来源，由 BillingSessionService 统一管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillingSessionService billingSessionService;
    private final PreConsumeQuotaService preConsumeQuotaService;
    private final RelayConsumeLogService relayConsumeLogService;
    private final UserService userService;
    private final UserNotifyService userNotifyService;

    /**
     * 创建 BillingSession 并执行预扣费      * <p>
     * 使用 subscription_first 偏好：优先检查订阅额度，不足时回退钱包。
     */
    public String preConsumeBilling(RelayInfo relayInfo, int preConsumedQuota) {
        BillingSessionService.BillingSession session = billingSessionService.newSessionWithPreference(
                relayInfo, preConsumedQuota, "subscription_first");
        if (session == null) {
            return "预扣费失败：额度不足";
        }
        relayInfo.setBilling(session);
        return null;
    }

    /**
     * Realtime 会话额度预检查      * <p>
     * 在 WebSocket Realtime 会话中，每次 response.done 后消费前，
     * 检查用户剩余额度是否足够。不足时抛出 RelayException 中断会话。
     *
     * @param relayInfo 中转上下文
     * @param quota     本次待消费额度
     */
    public void preWssConsumeQuota(RelayInfo relayInfo, int quota) {
        if (quota <= 0) {
            return;
        }
        int userId = relayInfo.getUserId();
        if (userId <= 0) {
            return;
        }
        User user = userService.getById(userId, true);
        if (user == null) {
            return;
        }
        long remainingQuota = user.getQuota() != null ? user.getQuota() : 0;
        if (remainingQuota < quota) {
            log.warn("Realtime 会话额度不足，中断会话：userId={}, remaining={}, required={}",
                    userId, remainingQuota, quota);
            throw CompatibleHandler.newApiError(
                    "额度不足，Realtime 会话即将关闭",
                    ErrorCode.INSUFFICIENT_USER_QUOTA, 402, true);
        }
    }

    /**
     * 执行计费结算      * <p>
     * 优先通过 BillingSession 结算（新路径），无 session 时回退到 PostConsumeQuota 路径（旧路径）。
     */
    public void settleBilling(RelayInfo relayInfo, int actualQuota) {
        if (relayInfo.getBilling() != null) {
            // 新路径：BillingSession 结算
            int preConsumed = relayInfo.getBilling().getPreConsumedQuota();
            int delta = actualQuota - preConsumed;
            if (delta > 0) {
                log.info("预扣费后补扣费：{} (实际消耗：{}，预扣费：{})", delta, actualQuota, preConsumed);
            } else if (delta < 0) {
                log.info("预扣费后返还扣费：{} (实际消耗：{}，预扣费：{})", -delta, actualQuota, preConsumed);
            }
            try {
                relayInfo.getBilling().settle(actualQuota);
            } catch (Exception e) {
                log.error("BillingSession 结算失败", e);
            }
        } else {
            // 旧路径：PostConsumeQuota
            int quotaDelta = actualQuota - relayInfo.getFinalPreConsumedQuota();
            if (quotaDelta != 0) {
                preConsumeQuotaService.postConsumeQuota(relayInfo, quotaDelta, relayInfo.getFinalPreConsumedQuota());
            }
        }

        // 结算后额度通知检查
        if (actualQuota != 0) {
            try {
                if ("subscription".equals(relayInfo.getBillingSource())) {
                    checkAndSendSubscriptionQuotaNotify(relayInfo);
                } else {
                    int preConsumed = relayInfo.getFinalPreConsumedQuota();
                    checkAndSendQuotaNotify(relayInfo, actualQuota - preConsumed, preConsumed);
                }
            } catch (Exception e) {
                log.warn("额度通知检查失败: {}", e.getMessage());
            }
        }
    }
    public void settleBillingAndLog(RelayInfo relayInfo, int actualQuota, Usage usage) {
        settleBilling(relayInfo, actualQuota);
        relayConsumeLogService.recordConsumeLog(relayInfo, actualQuota, usage);
    }

    // ======================== 额度通知 ======================== 
    /**
     * 钱包额度通知检查      * <p>
     * 结算后检查用户钱包剩余额度是否低于用户设定的阈值（quotaWarningThreshold），
     * 低于则通过 UserNotifyService 发送通知（邮件/Bark/Gotify/Webhook）。
     *
     * @param relayInfo   中转上下文
     * @param quotaDelta  结算差额（actualQuota - preConsumed）
     * @param preConsumed 预扣额度
     */
    private void checkAndSendQuotaNotify(RelayInfo relayInfo, int quotaDelta, int preConsumed) {
        int userId = relayInfo.getUserId();
        if (userId <= 0) {
            return;
        }
        User user = userService.getById(userId, true);
        if (user == null) {
            return;
        }

        UserNotifyService.UserSettingDto setting = userNotifyService.parseUserSetting(user.getSetting());
        Double threshold = setting.getQuotaWarningThreshold();
        // 用户未开启额度通知（阈值为空或 <= 0）
        if (threshold == null || threshold <= 0) {
            return;
        }

        long remainingQuota = user.getQuota() != null ? user.getQuota() : 0;
        long thresholdQuota = Math.round(threshold);
        if (remainingQuota >= thresholdQuota) {
            return;
        }

        String title = "额度不足提醒";
        String content = "您的剩余额度为 " + remainingQuota + "，已低于设定阈值 " + thresholdQuota;
        try {
            userNotifyService.notifyUser(userId, user.getEmail(), setting,
                    new UserNotifyService.NotifyDto(UserNotifyService.EVENT_QUOTA_EXCEED,
                            title, content, List.of(remainingQuota, thresholdQuota)));
        } catch (Exception e) {
            log.warn("发送钱包额度通知失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 订阅额度通知检查      * <p>
     * 结算后检查订阅剩余额度是否低于用户设定的阈值（quotaWarningThreshold），
     * 低于则通过 UserNotifyService 发送通知。
     *
     * @param relayInfo 中转上下文（含 subscriptionId / subscriptionAmountTotal 等字段）
     */
    private void checkAndSendSubscriptionQuotaNotify(RelayInfo relayInfo) {
        int userId = relayInfo.getUserId();
        int subscriptionId = relayInfo.getSubscriptionId();
        if (userId <= 0 || subscriptionId <= 0) {
            return;
        }
        User user = userService.getById(userId, true);
        if (user == null) {
            return;
        }

        UserNotifyService.UserSettingDto setting = userNotifyService.parseUserSetting(user.getSetting());
        Double threshold = setting.getQuotaWarningThreshold();
        if (threshold == null || threshold <= 0) {
            return;
        }

        // 计算订阅剩余额度：总额度 - (预扣后已用 + 结算差额)
        long amountTotal = relayInfo.getSubscriptionAmountTotal();
        long amountUsed = relayInfo.getSubscriptionAmountUsedAfterPreConsume()
                + relayInfo.getSubscriptionPostDelta();
        long remaining = amountTotal - amountUsed;
        long thresholdQuota = Math.round(threshold);
        if (remaining >= thresholdQuota) {
            return;
        }

        String title = "订阅额度不足提醒";
        String content = "您的订阅剩余额度为 " + remaining + "，已低于设定阈值 " + thresholdQuota;
        try {
            userNotifyService.notifyUser(userId, user.getEmail(), setting,
                    new UserNotifyService.NotifyDto(UserNotifyService.EVENT_QUOTA_EXCEED,
                            title, content, List.of(remaining, thresholdQuota)));
        } catch (Exception e) {
            log.warn("发送订阅额度通知失败 userId={}: {}", userId, e.getMessage());
        }
    }
}
