package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系统设置模块入参对象（IPO），对应 OptionController 各端点请求体。
 * <p>
 * @Size 上限对齐 options 表 DDL 列长：key varchar(128)。value 为 text 类型，
 * 给合理业务上限防超长输入。Waffo 配置字段为外部商户凭证，给合理上限。
 */
public class OptionIPO {

    @Data
    public static class Update {
        @NotBlank(message = "key不能为空")
        @Size(max = 128, message = "key 长度不能超过 128 字符")
        private String key;

        @NotNull(message = "value不能为空")
        // value 为 text 类型，限定合理上限防超长配置
        private Object value;
    }

    @Data
    public static class PaymentCompliance {
        @NotNull(message = "confirmed不能为空")
        private Boolean confirmed;
    }

    @Data
    public static class WaffoCatalog {
        @Size(max = 128, message = "merchantId 长度不能超过 128 字符")
        private String merchantId;

        @Size(max = 512, message = "privateKey 长度不能超过 512 字符")
        private String privateKey;
    }

    @Data
    public static class WaffoPair {
        @Size(max = 128, message = "merchantId 长度不能超过 128 字符")
        private String merchantId;

        @Size(max = 512, message = "privateKey 长度不能超过 512 字符")
        private String privateKey;

        @Size(max = 1024, message = "returnUrl 长度不能超过 1024 字符")
        private String returnUrl;
    }

    @Data
    public static class WaffoSave {
        @Size(max = 128, message = "merchantId 长度不能超过 128 字符")
        private String merchantId;

        @Size(max = 512, message = "privateKey 长度不能超过 512 字符")
        private String privateKey;

        @Size(max = 1024, message = "returnUrl 长度不能超过 1024 字符")
        private String returnUrl;

        @Size(max = 128, message = "storeId 长度不能超过 128 字符")
        private String storeId;

        @Size(max = 128, message = "productId 长度不能超过 128 字符")
        private String productId;
    }

    @Data
    public static class WaffoSubProduct {
        @NotBlank(message = "套餐名称不能为空")
        @Size(max = 128, message = "套餐名称长度不能超过 128 字符")
        private String name;

        @NotBlank(message = "套餐价格不能为空")
        @Size(max = 32, message = "套餐价格长度不能超过 32 字符")
        private String amount;
    }
}
