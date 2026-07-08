package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * OAuth 模块入参对象（IPO），对应 OAuthController 各端点请求体。
 * <p>
 * @Size 上限对齐 users 表 DDL 列长：email varchar(50)。
 */
public class OAuthIPO {

    @Data
    public static class EmailBind {
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 50, message = "邮箱长度不能超过 50 字符")
        private String email;

        @NotBlank(message = "验证码不能为空")
        @Size(max = 20, message = "验证码长度不能超过 20 字符")
        private String code;
    }

    @Data
    public static class EmailUnbind {
        @NotBlank(message = "验证码不能为空")
        @Size(max = 20, message = "验证码长度不能超过 20 字符")
        private String code;
    }

    @Data
    public static class WechatBind {
        @Size(max = 512, message = "code 长度不能超过 512 字符")
        private String code;
    }
}
