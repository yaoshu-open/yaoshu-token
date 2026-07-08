package yaoshu.token.config.operation;

import lombok.Data;

/**
 * 支付设置 POJO  */
@Data
public class PaymentSettingConfig {

    /** 是否启用支付 */
    private boolean enabled;
    /** 支付平台 */
    private String platform;
    /** 支付回调地址 */
    private String callbackUrl;
    /** 最小充值金额 */
    private double minAmount = 1.0;
    /** 默认充值额度（每 1 USD） */
    private double quotaPerUnit = 500_000;
}
