package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.config.GeneralSettingConfig;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.constant.ModelRatioConstants;
import yaoshu.token.mapper.TokenMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.pojo.entity.Token;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.vo.DashboardVO.SubscriptionResponse;
import yaoshu.token.pojo.vo.DashboardVO.UsageResponse;

/**
 * Dashboard Billing 服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserMapper userMapper;
    private final TokenMapper tokenMapper;

    /**
     * 获取订阅信息      */
    public SubscriptionResponse getSubscription(Integer userId, Integer tokenId) {
        long remainQuota;
        long usedQuota;
        long expiredTime = 0;
        Token token = null;

        if (CommonConstants.displayTokenStatEnabled && tokenId != null) {
            token = tokenMapper.selectById(tokenId);
            if (token == null) {
                return SubscriptionResponse.builder()
                        .object("billing_subscription")
                        .hasPaymentMethod(true)
                        .softLimitUsd(0.0)
                        .hardLimitUsd(0.0)
                        .systemHardLimitUsd(0.0)
                        .accessUntil(0L)
                        .build();
            }
            expiredTime = token.getExpiredTime() != null ? token.getExpiredTime() : 0;
            remainQuota = token.getRemainQuota() != null ? token.getRemainQuota() : 0;
            usedQuota = token.getUsedQuota() != null ? token.getUsedQuota() : 0;
        } else {
            User user = userMapper.selectById(userId);
            if (user == null) {
                return SubscriptionResponse.builder()
                        .object("billing_subscription")
                        .hasPaymentMethod(true)
                        .softLimitUsd(0.0)
                        .hardLimitUsd(0.0)
                        .systemHardLimitUsd(0.0)
                        .accessUntil(0L)
                        .build();
            }
            remainQuota = user.getQuota() != null ? user.getQuota() : 0;
            usedQuota = user.getUsedQuota() != null ? user.getUsedQuota() : 0;
        }

        if (expiredTime <= 0) {
            expiredTime = 0;
        }

        long quota = remainQuota + usedQuota;
        double amount = computeDisplayAmount(quota);

        if (token != null && Boolean.TRUE.equals(token.getUnlimitedQuota())) {
            amount = 100_000_000;
        }

        return SubscriptionResponse.builder()
                .object("billing_subscription")
                .hasPaymentMethod(true)
                .softLimitUsd(amount)
                .hardLimitUsd(amount)
                .systemHardLimitUsd(amount)
                .accessUntil(expiredTime)
                .build();
    }

    /**
     * 获取用量信息      */
    public UsageResponse getUsage(Integer userId, Integer tokenId) {
        long quota;

        if (CommonConstants.displayTokenStatEnabled && tokenId != null) {
            Token token = tokenMapper.selectById(tokenId);
            quota = (token != null && token.getUsedQuota() != null) ? token.getUsedQuota() : 0;
        } else {
            User user = userMapper.selectById(userId);
            quota = (user != null && user.getUsedQuota() != null) ? user.getUsedQuota() : 0;
        }

        double amount = computeDisplayAmount(quota);

        return UsageResponse.builder()
                .object("list")
                .totalUsage(amount * 100) // unit: 0.01 dollar
                .build();
    }

    /**
     * 根据额度展示类型将配额转换为展示金额      */
    private double computeDisplayAmount(long quota) {
        double amount = (double) quota;
        String displayType = GeneralSettingConfig.getQuotaDisplayType();

        switch (displayType) {
            case GeneralSettingConfig.QUOTA_DISPLAY_CNY:
                amount = amount / CommonConstants.quotaPerUnit * ModelRatioConstants.USD2RMB;
                break;
            case GeneralSettingConfig.QUOTA_DISPLAY_TOKENS:
                // tokens 保持原值
                break;
            default:
                amount = amount / CommonConstants.quotaPerUnit;
                break;
        }
        return amount;
    }
}
