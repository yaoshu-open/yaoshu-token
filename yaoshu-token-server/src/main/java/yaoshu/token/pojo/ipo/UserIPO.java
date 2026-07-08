package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户模块入参对象（IPO），对应 UserController 各端点请求体。
 * <p>
 * 字段全部 camelCase，替代原 Go 风格 snake_case 的 Map body 裸参数模式。
 * <p>
 * @Size 上限对齐 users 表 DDL 列长：username varchar(20)、password varchar(255)、
 * display_name varchar(20)、email varchar(50)、aff_code varchar(32)。
 */
public class UserIPO {

    @Data
    public static class Register {
        @NotBlank(message = "用户名不能为空")
        @Size(max = 20, message = "用户名长度不能超过 20 字符")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 255, message = "密码长度必须在 8-255 个字符之间")
        private String password;

        @Email(message = "邮箱格式不正确")
        @Size(max = 50, message = "邮箱长度不能超过 50 字符")
        private String email;

        @Size(max = 20, message = "验证码长度不能超过 20 字符")
        private String verificationCode;

        @Size(max = 32, message = "邀请码长度不能超过 32 字符")
        private String affCode;
    }

    @Data
    public static class Login {
        @NotBlank(message = "用户名不能为空")
        @Size(max = 20, message = "用户名长度不能超过 20 字符")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(max = 255, message = "密码长度不能超过 255 字符")
        private String password;
    }

    @Data
    public static class UpdateSelf {
        @Size(max = 20, message = "显示名称长度不能超过 20 字符")
        private String displayName;

        @Email(message = "邮箱格式不正确")
        @Size(max = 50, message = "邮箱长度不能超过 50 字符")
        private String email;

        @Size(min = 8, max = 255, message = "密码长度必须在 8-255 个字符之间")
        private String password;

        // sidebarModules 序列化为 JSON 存入 users.setting（text），限定合理上限防超长 JSON
        @Size(max = 2000, message = "侧边栏模块配置长度不能超过 2000 字符")
        private String sidebarModules;

        // 语言码格式：en / zh-CN / zh-TW（ISO 639-1 可选连 ISO 3166-1）
        @Size(max = 10, message = "语言码长度不能超过 10 字符")
        @Pattern(regexp = "^[a-zA-Z]{2}(-[a-zA-Z]{2,3})?$", message = "语言码格式不正确")
        private String language;
    }

    @Data
    public static class AffTransfer {
        @NotNull(message = "转移额度不能为空")
        @Min(value = 1, message = "转移额度必须大于0")
        @Max(value = Integer.MAX_VALUE, message = "转移额度超出上限")
        private Integer quota;
    }
}
