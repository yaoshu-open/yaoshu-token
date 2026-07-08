package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 双因素认证模块入参对象（IPO），对应 TwoFaController 各端点请求体。
 * <p>
 * 验证码存于 users.setting（text），@Size 给合理上限防超长输入。
 */
public class TwoFaIPO {

    @Data
    public static class CodeVerify {
        @NotBlank(message = "验证码不能为空")
        @Size(max = 20, message = "验证码长度不能超过 20 字符")
        private String code;
    }
}
