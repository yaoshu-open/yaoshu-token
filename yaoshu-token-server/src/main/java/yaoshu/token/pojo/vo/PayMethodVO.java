package yaoshu.token.pojo.vo;

import cn.hutool.v7.core.annotation.Alias;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 充值支付方式 VO  * <p>
 * 数据库 options.PayMethods 存储的 JSON 使用 snake_case（{@code min_topup}），
 * 通过 {@link JSONField} + {@link Alias} 联合声明让 Convert 解析时识别下划线 key，
 * 响应序列化由 Jackson 按字段名输出 camelCase（{@code minTopup}），满足全局 camelCase 契约。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayMethodVO {

    private String name;

    private String type;

    private String color;

    /**
     * 最小充值额度。数据库存储为字符串（Go 原版 map[string]string），保留字符串类型以兼容历史配置。
     */
    @JSONField(name = "min_topup")
    @Alias("min_topup")
    private String minTopup;
}
