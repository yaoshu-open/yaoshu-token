package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员用户管理入参对象（IPO），对应 AdminController 各端点请求体。
 * <p>
 * @Size 上限对齐 users 表 DDL 列长：username varchar(20)、password varchar(255)、
 * display_name varchar(20)、email varchar(50)、group varchar(64)。
 */
public class AdminIPO {

    @Data
    public static class CreateUser {
        @NotBlank(message = "用户名不能为空")
        @Size(max = 20, message = "用户名长度不能超过 20 字符")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 255, message = "密码长度必须在 8-255 个字符之间")
        private String password;

        @Size(max = 20, message = "显示名称长度不能超过 20 字符")
        private String displayName;

        private Integer role;

        @Email(message = "邮箱格式不正确")
        @Size(max = 50, message = "邮箱长度不能超过 50 字符")
        private String email;

        @Size(max = 64, message = "group 长度不能超过 64 字符")
        private String group;

        @Min(value = 0, message = "配额不能为负")
        private Integer quota;
    }

    @Data
    public static class ManageUser {
        @NotNull(message = "用户ID不能为空")
        private Integer id;

        @NotBlank(message = "action不能为空")
        @Size(max = 64, message = "action 长度不能超过 64 字符")
        private String action;

        private Integer value;

        @Size(max = 64, message = "mode 长度不能超过 64 字符")
        private String mode;
    }

    @Data
    public static class UpdateUser {
        @NotNull(message = "用户ID不能为空")
        private Integer id;

        @Size(max = 20, message = "用户名长度不能超过 20 字符")
        private String username;

        @Size(min = 8, max = 255, message = "密码长度必须在 8-255 个字符之间")
        private String password;

        @Size(max = 20, message = "显示名称长度不能超过 20 字符")
        private String displayName;

        private Integer role;

        // 用户状态：1=启用 2=禁用 3=已删除，值域校验由 Service 层处理
        @Min(value = 1, message = "状态值无效")
        private Integer status;

        @Email(message = "邮箱格式不正确")
        @Size(max = 50, message = "邮箱长度不能超过 50 字符")
        private String email;

        @Size(max = 64, message = "group 长度不能超过 64 字符")
        private String group;

        @Min(value = 0, message = "配额不能为负")
        private Integer quota;
    }

    @Data
    public static class CompleteTopup {
        @NotBlank(message = "tradeNo不能为空")
        @Size(max = 128, message = "tradeNo 长度不能超过 128 字符")
        private String tradeNo;
    }
}
