package yaoshu.token.pojo.ipo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部署管理模块入参对象（IPO），对应 DeploymentController 可类型化端点。
 * <p>
 * 注意：price-estimation / create / update / extend 端点为外部 API 透传，
 * body 为动态 payload，保留 Map 透传至 DeploymentService。
 * <p>
 * 部署管理为 io.net 外部 API 透传，本地无表，@Size 给合理业务上限防超长输入。
 */
public class DeploymentIPO {

    @Data
    public static class TestConnection {
        @Size(max = 512, message = "apiKey 长度不能超过 512 字符")
        private String apiKey;
    }

    @Data
    public static class UpdateName {
        @NotBlank(message = "deployment name cannot be empty")
        @Size(max = 128, message = "deployment name 长度不能超过 128 字符")
        private String name;
    }
}
