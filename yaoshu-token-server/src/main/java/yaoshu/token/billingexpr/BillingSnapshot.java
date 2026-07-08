package yaoshu.token.billingexpr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 计费规则快照（预扣费时冻结）。  * <p>
 * 完全可序列化，不含编译后的程序指针。结算时用此快照重新执行表达式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSnapshot {
    /** 计费模式，tiered_expr 表示使用分层表达式计费 */
    private String billingMode;
    private String modelName;
    /** 表达式字符串 */
    private String exprString;
    /** 表达式 SHA-256 哈希（缓存键） */
    private String exprHash;
    /** 分组倍率 */
    private double groupRatio;
    /** 预估输入 token 数 */
    private int estimatedPromptTokens;
    /** 预估输出 token 数 */
    private int estimatedCompletionTokens;
    /** 预估配额（分组前） */
    private double estimatedQuotaBeforeGroup;
    /** 预估配额（分组后） */
    private int estimatedQuotaAfterGroup;
    /** 预估阶梯名称 */
    private String estimatedTier;
    /** 每单位配额对应的金额系数 */
    private double quotaPerUnit;
    /** 表达式版本号 */
    private int exprVersion;
}
