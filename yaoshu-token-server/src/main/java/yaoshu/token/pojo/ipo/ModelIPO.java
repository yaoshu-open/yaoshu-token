package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模型管理模块入参对象（IPO），对应 ModelController 各端点请求体。
 * <p>
 * @Size 上限对齐 models 表 DDL 列长：model_name varchar(128)、icon varchar(128)、
 * tags varchar(255)。description/endpoints 为 text 类型，给合理业务上限防超长输入。
 */
public class ModelIPO {

    @Data
    public static class Create {
        @NotBlank(message = "模型名称不能为空")
        @Size(max = 128, message = "模型名称长度不能超过 128 字符")
        private String modelName;

        @Size(max = 2000, message = "描述长度不能超过 2000 字符")
        private String description;

        @Size(max = 128, message = "icon 长度不能超过 128 字符")
        private String icon;

        @Size(max = 255, message = "tags 长度不能超过 255 字符")
        private String tags;

        private Integer vendorId;

        @Size(max = 10000, message = "endpoints 长度不能超过 10000 字符")
        private String endpoints;

        // 模型状态：1=启用 2=禁用，值域校验由 Service 层处理
        @Min(value = 1, message = "状态值无效")
        private Integer status;

        private Integer syncOfficial;

        private Integer nameRule;

        /** 最大上下文窗口token数（null=不设置） */
        private Integer maxContext;
    }

    @Data
    public static class Update {
        @NotNull(message = "模型ID不能为空")
        private Integer id;

        @Size(max = 128, message = "模型名称长度不能超过 128 字符")
        private String modelName;

        @Size(max = 2000, message = "描述长度不能超过 2000 字符")
        private String description;

        @Size(max = 128, message = "icon 长度不能超过 128 字符")
        private String icon;

        @Size(max = 255, message = "tags 长度不能超过 255 字符")
        private String tags;

        private Integer vendorId;

        @Size(max = 10000, message = "endpoints 长度不能超过 10000 字符")
        private String endpoints;

        @Min(value = 1, message = "状态值无效")
        private Integer status;

        private Integer syncOfficial;

        private Integer nameRule;

        /** 最大上下文窗口token数（null=不更新） */
        private Integer maxContext;
    }

    @Data
    public static class SyncUpstream {
        // 语言码格式：en / zh-CN / zh-TW
        @Size(max = 10, message = "locale 长度不能超过 10 字符")
        private String locale;

        private Object overwrite;
    }
}
