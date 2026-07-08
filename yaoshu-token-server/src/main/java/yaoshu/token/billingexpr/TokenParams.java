package yaoshu.token.billingexpr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 计费表达式 token 参数。  * <p>
 * P/C 为"兜底变量"——代表未被表达式单独定价的 token。
 * 系统根据表达式实际使用的变量，自动从 P/C 中减去对应子类别 token。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenParams {
    /** 输入 token（计价用），自动排除表达式中单独计价的子类别 */
    public double p;
    /** 输出 token（计价用），自动排除表达式中单独计价的子类别 */
    public double c;
    /** 输入上下文总长度（条件判断用），不受自动排除影响 */
    public double len;
    /** 缓存命中（读取）token */
    public double cr;
    /** 缓存创建 token（5分钟 TTL / 通用） */
    public double cc;
    /** 缓存创建 token — 1小时 TTL（Claude 专用） */
    public double cc1h;
    /** 图片输入 token */
    public double img;
    /** 图片输出 token */
    public double imgO;
    /** 音频输入 token */
    public double ai;
    /** 音频输出 token */
    public double ao;
}
