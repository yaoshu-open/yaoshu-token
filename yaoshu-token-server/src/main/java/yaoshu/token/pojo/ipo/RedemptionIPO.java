package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 兑换码管理模块入参对象（IPO），对应 RedemptionController 各端点请求体。
 * <p>
 * @Size 上限对齐 redemptions 表 DDL 列长：name varchar(128)。quota/count 为 int 类型，
 * 加 @Min/@Max 防溢出与恶意批量创建。
 */
public class RedemptionIPO {

    @Data
    public static class Create {
        @NotBlank(message = "兑换码名称不能为空")
        @Size(max = 128, message = "兑换码名称长度不能超过 128 字符")
        private String name;

        @Min(value = 1, message = "配额必须大于0")
        @Max(value = Integer.MAX_VALUE, message = "配额超出上限")
        private Integer quota;

        // 批量创建数量上限防恶意批量创建，业务上限由 Service 层校验
        @Min(value = 1, message = "创建数量必须大于0")
        @Max(value = 1000, message = "单次创建数量不能超过 1000")
        private Integer count;

        // 过期时间戳（秒），非负
        @Min(value = 0, message = "过期时间无效")
        private Long expiredTime;
    }

    @Data
    public static class Update {
        @NotNull(message = "兑换码ID不能为空")
        private Integer id;

        // 兑换码状态：1=启用 2=禁用 3=已兑换，值域校验由 Service 层处理
        @Min(value = 1, message = "状态值无效")
        @Max(value = 3, message = "状态值无效")
        private Integer status;

        @Size(max = 128, message = "兑换码名称长度不能超过 128 字符")
        private String name;

        @Min(value = 0, message = "配额不能为负")
        @Max(value = Integer.MAX_VALUE, message = "配额超出上限")
        private Integer quota;

        @Min(value = 0, message = "过期时间无效")
        private Long expiredTime;
    }
}
