package yaoshu.token.billingexpr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分层结算结果。  * <p>
 * 运行 tiered 表达式后的完整结算信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TieredResult {
    /** 实际配额（分组前） */
    private double actualQuotaBeforeGroup;
    /** 实际配额（分组后） */
    private int actualQuotaAfterGroup;
    /** 匹配的阶梯名称 */
    private String matchedTier;
    /** 是否跨越了预估阶梯 */
    private boolean crossedTier;
}
