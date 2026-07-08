package yaoshu.token.config;

import lombok.Data;

/**
 * 分层计费配置 POJO  */
@Data
public class TieredBillingConfig {

    /** 是否启用分层计费 */
    private boolean enabled;
    /** 分层阈值（token数） */
    private java.util.List<Long> thresholds;
    /** 分层折扣率 */
    private java.util.List<Double> discountRates;
}
