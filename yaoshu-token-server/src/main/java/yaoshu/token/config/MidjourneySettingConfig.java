package yaoshu.token.config;

import lombok.Data;

/**
 * Midjourney 设置 POJO  */
@Data
public class MidjourneySettingConfig {

    /** 是否启用 Midjourney */
    private boolean enabled;
    /** Midjourney 代理 URL */
    private String proxyUrl;
    /** 默认图片比例 */
    private String defaultAspectRatio = "1:1";
    /** 默认版本 */
    private String defaultVersion = "6.1";
    /** 超时时间（秒） */
    private int timeoutSeconds = 300;
}
