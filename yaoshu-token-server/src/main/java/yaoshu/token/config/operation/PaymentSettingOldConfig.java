package yaoshu.token.config.operation;

import lombok.Data;

/**
 * 旧版支付设置 POJO  */
@Data
public class PaymentSettingOldConfig {

    /** 是否启用旧版支付 */
    private boolean enabled;
    /** 支付网关 URL */
    private String gatewayUrl;
    /** 商户 ID */
    private String merchantId;
    /** 密钥 */
    private String secretKey;
}
