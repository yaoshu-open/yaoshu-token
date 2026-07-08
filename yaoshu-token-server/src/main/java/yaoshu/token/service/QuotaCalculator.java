package yaoshu.token.service;

import org.springframework.stereotype.Component;
import yaoshu.token.constant.CommonConstants;

/**
 * 信任配额计算器  */
@Component
public class QuotaCalculator {

    /** 获取信任配额（10 * QuotaPerUnit） */
    public static int getTrustQuota() {
        return (int) (10 * CommonConstants.quotaPerUnit);
    }
}
