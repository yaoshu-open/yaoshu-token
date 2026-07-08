package yaoshu.token.config.operation;

import lombok.Data;

/**
 * Token 设置 POJO  */
@Data
public class TokenSettingConfig {

    /** 是否启用 token 过期 */
    private boolean tokenExpirationEnabled;
    /** Token 默认有效期（秒） */
    private long tokenDefaultTTLSeconds;
    /** 是否允许自定义 token 名称 */
    private boolean allowCustomTokenName;
}
