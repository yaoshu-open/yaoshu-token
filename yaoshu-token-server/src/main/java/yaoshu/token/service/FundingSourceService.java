package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.mapper.UserSubscriptionMapper;
import yaoshu.token.pojo.entity.UserSubscription;

import java.util.ArrayList;
import java.util.List;

/**
 * 资金来源检测服务
 * <p>
 * 资金来源接口本身（FundingSource/WalletFunding/SubscriptionFunding）由 Session-16 翻译完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundingSourceService {

    private final UserSubscriptionMapper userSubscriptionMapper;

    /** 资金来源类型常量 */
    public static final String SOURCE_WALLET = "wallet";
    public static final String SOURCE_SUBSCRIPTION = "subscription";

    /**
     * 获取用户可用资金来源，对应 BillingSession 构造前的预判逻辑
     * <p>
     * 规则：钱包始终可用；订阅仅在用户有活跃订阅时可用。
     *
     * @param userId 用户 ID
     * @return 可用资金来源列表（至少包含 wallet）
     */
    public List<String> getAvailableSources(int userId) {
        List<String> sources = new ArrayList<>();
        sources.add(SOURCE_WALLET);

        if (hasActiveSubscription(userId)) {
            sources.add(SOURCE_SUBSCRIPTION);
        }

        return sources;
    }

    /**
     * 检查资金来源是否可用
     */
    public boolean isSourceAvailable(int userId, String source) {
        if (source == null) {
            return false;
        }
        List<String> sources = getAvailableSources(userId);
        return sources.contains(source);
    }

    /**
     * 检查用户是否有活跃订阅（余额 > 0 且未过期）
     * <p>
     */
    private boolean hasActiveSubscription(int userId) {
        if (userId <= 0) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        Long count = userSubscriptionMapper.selectCount(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, userId)
                        .eq(UserSubscription::getStatus, "active")
                        .gt(UserSubscription::getEndTime, now)
        );
        return count != null && count > 0;
    }
}
