package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 供应商元数据管理入参对象（IPO），对应 VendorController 各端点请求体。
 * <p>
 * @Size 上限对齐 vendors 表 DDL 列长：name varchar(128)、icon varchar(128)。
 * description 为 text 类型，给合理业务上限防超长输入。
 */
public class VendorIPO {

    @Data
    public static class Create {
        @NotBlank(message = "供应商名称不能为空")
        @Size(max = 128, message = "供应商名称长度不能超过 128 字符")
        private String name;

        @Size(max = 2000, message = "描述长度不能超过 2000 字符")
        private String description;

        @Size(max = 128, message = "icon 长度不能超过 128 字符")
        private String icon;

        // 供应商状态：1=启用 2=禁用，值域校验由 Service 层处理
        @Min(value = 1, message = "状态值无效")
        private Integer status;
    }

    @Data
    public static class Update {
        @NotNull(message = "供应商ID不能为空")
        private Integer id;

        @Size(max = 128, message = "供应商名称长度不能超过 128 字符")
        private String name;

        @Size(max = 2000, message = "描述长度不能超过 2000 字符")
        private String description;

        @Size(max = 128, message = "icon 长度不能超过 128 字符")
        private String icon;

        @Min(value = 1, message = "状态值无效")
        private Integer status;
    }
}
