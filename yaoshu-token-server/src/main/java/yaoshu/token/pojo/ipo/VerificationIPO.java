package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 安全验证模块入参对象（IPO），对应 VerificationController 通用验证端点。
 * <p>
 * method/code 为验证流程标识，@Size 给合理上限防超长输入，值域校验由 Service 层处理。
 */
public class VerificationIPO {

    @Data
    public static class UniversalVerify {
        @NotBlank(message = "method为必填")
        @Size(max = 64, message = "method 长度不能超过 64 字符")
        private String method;

        @Size(max = 20, message = "验证码长度不能超过 20 字符")
        private String code;
    }
}
