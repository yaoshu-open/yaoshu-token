package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Token 管理模块入参对象（IPO），对应 TokenController 各端点请求体。
 * <p>
 * @Size 上限对齐 tokens 表 DDL 列长：name varchar(128)、allow_ips varchar(255)、
 * group varchar(64)。model_limits 为 text 类型，给合理业务上限防超长输入。
 */
public class TokenIPO {

    @Data
    public static class Create {
        @NotBlank(message = "Token名称不能为空")
        @Size(max = 128, message = "Token名称长度不能超过 128 字符")
        private String name;

        // 过期时间戳（秒），-1 表示永不过期
        @Min(value = -1, message = "过期时间无效")
        private Long expiredTime;

        @Min(value = 0, message = "剩余配额不能为负")
        private Long remainQuota;

        private Boolean unlimitedQuota;

        @Size(max = 10000, message = "model_limits 长度不能超过 10000 字符")
        private String modelLimits;

        private Boolean modelLimitsEnabled;

        @Size(max = 255, message = "allow_ips 长度不能超过 255 字符")
        private String allowIps;

        @Size(max = 64, message = "group 长度不能超过 64 字符")
        private String group;

        private Boolean crossGroupRetry;
    }

    @Data
    public static class Update {
        @NotNull(message = "Token ID不能为空")
        private Integer id;

        @Size(max = 128, message = "Token名称长度不能超过 128 字符")
        private String name;

        @Min(value = -1, message = "过期时间无效")
        private Long expiredTime;

        @Min(value = 0, message = "剩余配额不能为负")
        private Long remainQuota;

        private Boolean unlimitedQuota;

        private Boolean modelLimitsEnabled;

        @Size(max = 10000, message = "model_limits 长度不能超过 10000 字符")
        private String modelLimits;

        @Size(max = 255, message = "allow_ips 长度不能超过 255 字符")
        private String allowIps;

        @Size(max = 64, message = "group 长度不能超过 64 字符")
        private String group;

        private Boolean crossGroupRetry;

        // Token 状态：1=启用 2=禁用，值域校验由 Service 层处理
        @Min(value = 1, message = "状态值无效")
        private Integer status;
    }

    @Data
    public static class BatchDelete {
        @NotEmpty(message = "ids不能为空")
        private List<Integer> ids;
    }

    @Data
    public static class BatchGetKeys {
        @NotEmpty(message = "ids不能为空")
        private List<Integer> ids;
    }
}
