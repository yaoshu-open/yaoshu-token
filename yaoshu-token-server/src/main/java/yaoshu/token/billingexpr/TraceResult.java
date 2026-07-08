package yaoshu.token.billingexpr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * tier() 函数执行期间的侧通道信息。  * <p>
 * Expr 本身是计费逻辑的唯一真实来源，TraceResult 仅记录匹配的阶梯。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraceResult {
    /** 匹配的阶梯名称 */
    private String matchedTier;
    /** 阶梯成本 */
    private double cost;
}
