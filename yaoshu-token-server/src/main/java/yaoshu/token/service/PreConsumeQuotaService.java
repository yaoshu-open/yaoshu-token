package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.mapper.TokenMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.relay.common.RelayInfo;

/**
 * 预消费配额服务  * <p>
 * 核心流程：检查用户/Token 配额是否充足 → 充足且高于信任阈值则跳过预扣 → 否则原子扣减配额
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreConsumeQuotaService {

    private final UserService userService;
    private final TokenMapper tokenMapper;
    private final QuotaCalculator quotaCalculator;

    /**
     * 返还预扣费配额（异步）      */
    public void returnPreConsumedQuota(RelayInfo relayInfo) {
        int preConsumed = relayInfo.getFinalPreConsumedQuota();
        if (preConsumed != 0) {
            log.info("用户 {} 请求失败, 返还预扣费额度 {}", relayInfo.getUserId(), preConsumed);
            postConsumeQuota(relayInfo, -preConsumed, 0);
        }
    }

    /**
     * 预扣费配额检查与执行      * <p>
     * 返回 null 表示成功（预扣费额度写入 relayInfo.finalPreConsumedQuota），
     * 返回字符串表示错误描述。
     */
    public String preConsumeQuota(RelayInfo relayInfo, int preConsumedQuota) {
        int userId = relayInfo.getUserId();
        long userQuota = userService.getUserQuota(userId);

        if (userQuota <= 0) {
            return "用户额度不足, 剩余额度: " + userQuota;
        }
        if (userQuota - preConsumedQuota < 0) {
            return "预扣费额度失败, 用户剩余额度: " + userQuota + ", 需要预扣费额度: " + preConsumedQuota;
        }

        relayInfo.setUserQuota(userQuota);
        int trustQuota = quotaCalculator.getTrustQuota();

        if (userQuota > trustQuota) {
            // 用户额度充足，判断 Token 额度是否充足
            if (!relayInfo.isTokenUnlimited()) {
                // 非无限令牌，判断令牌额度
                long tokenQuota = getTokenRemainQuota(relayInfo.getTokenId());
                if (tokenQuota > trustQuota) {
                    preConsumedQuota = 0;
                    log.info("用户 {} 剩余额度 {} 且令牌 {} 额度 {} 充足, 信任且不需要预扣费",
                            userId, userQuota, relayInfo.getTokenId(), tokenQuota);
                }
            } else {
                // 无限令牌 + 用户额度充足 → 信任跳过
                preConsumedQuota = 0;
                log.info("用户 {} 额度充足且为无限额度令牌, 信任且不需要预扣费", userId);
            }
        }

        if (preConsumedQuota > 0) {
            // 预扣 Token 配额
            int decreased = tokenMapper.decreaseRemainQuota(relayInfo.getTokenId(), preConsumedQuota);
            if (decreased <= 0) {
                return "预扣 Token 配额失败";
            }
            // 预扣用户配额（原子操作）
            userService.decreaseUserQuota(userId, preConsumedQuota);
            log.info("用户 {} 预扣费 {}, 预扣费后剩余额度: {}", userId, preConsumedQuota, userQuota - preConsumedQuota);
        }

        relayInfo.setFinalPreConsumedQuota(preConsumedQuota);
        return null; // 成功
    }

    /**
     * 后结算配额（delta 更新）      */
    @Transactional(rollbackFor = Exception.class)
    public void postConsumeQuota(RelayInfo relayInfo, int quotaDelta, int preConsumedQuota) {
        if (quotaDelta != 0) {
            userService.deltaUpdateQuota(relayInfo.getUserId(), quotaDelta);
        }
        int actualQuota = preConsumedQuota + quotaDelta;
        if (actualQuota > 0) {
            tokenMapper.increaseUsedQuota(relayInfo.getTokenId(), actualQuota);
        }
    }

    private long getTokenRemainQuota(int tokenId) {
        var token = tokenMapper.selectById(tokenId);
        if (token == null || token.getRemainQuota() == null) {
            return 0;
        }
        // 无限配额返回一个大数
        if (Boolean.TRUE.equals(token.getUnlimitedQuota())) {
            return Long.MAX_VALUE;
        }
        return token.getRemainQuota();
    }
}
