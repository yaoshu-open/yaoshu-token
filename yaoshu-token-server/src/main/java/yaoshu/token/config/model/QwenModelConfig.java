package yaoshu.token.config.model;

import lombok.Data;

/**
 * Qwen 模型设置 POJO  */
@Data
public class QwenModelConfig {

    /** 是否启用 Qwen 增强模式 */
    private boolean enhancedEnabled;
    /** Qwen API Base URL */
    private String baseUrl;
    /** 是否启用 DashScope 兼容 */
    private boolean dashScopeCompat;
}
