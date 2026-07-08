package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * POST /api/setup 请求体  * <p>
 * 字段命名遵循项目驼峰命名重构规范（camelCase）。
 * 注意：options 表的 key 名（"SelfUseModeEnabled"/"DemoSiteEnabled"）是业务数据标识符，
 * 沿用 Go 原项目不变；此处仅 API 入参字段名采用 camelCase。
 * <p>
 * @Size 上限：username max=12 沿用 Go 原项目业务规则（setup.go 显式校验 len > 12），
 * 比 DB users.username varchar(20) 更严；password 对齐 users.password varchar(255)。
 */
@Data
public class SetupIPO {

    // username max=12 沿用 Go setup.go 业务规则（比 DB varchar(20) 更严）
    @NotBlank
    @Size(max = 12, message = "用户名长度不能超过12个字符")
    private String username;

    @NotBlank
    @Size(min = 8, message = "密码长度至少为8个字符")
    @Size(max = 255, message = "密码长度不能超过 255 字符")
    private String password;

    @NotBlank
    @Size(max = 255, message = "确认密码长度不能超过 255 字符")
    private String confirmPassword;

    private Boolean selfUseModeEnabled;

    private Boolean demoSiteEnabled;
}
