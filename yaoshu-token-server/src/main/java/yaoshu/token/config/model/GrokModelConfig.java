package yaoshu.token.config.model;

import lombok.Data;

/**
 * Grok 模型设置 POJO  */
@Data
public class GrokModelConfig {

    /** 是否启用 Grok */
    private boolean enabled;
    /** Grok API Base URL */
    private String baseUrl;
    /** 是否启用 Fun 模式 */
    private boolean funModeEnabled;
    /** 违规扣费阈值（USD） */
    private double violationFee = 0.01;
}
