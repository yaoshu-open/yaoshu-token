package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 支付模块入参对象（IPO），对应 PaymentController 各端点请求体。
 */
public class PaymentIPO {

    @Data
    public static class WaffoPancakePay {
        @NotNull(message = "充值数量不能为空")
        @Min(value = 1, message = "充值数量必须大于0")
        // Long 类型上限防溢出，业务上限由 Service 层校验
        @Max(value = Long.MAX_VALUE, message = "充值数量超出上限")
        private Long amount;
    }
}
