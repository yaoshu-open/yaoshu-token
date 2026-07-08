package yaoshu.token.config;

import lombok.Data;

/**
 * 控制台设置（全局配置管理器 + 校验），
 */
public final class GlobalConfigManager {

    private GlobalConfigManager() {
    }

    /**
     * 注册配置对象到全局管理器      * <p>
     * 翻译说明：Go 使用全局 config.GlobalConfig 注册所有 setting 子模块。
     * Java 通过 Spring @ConfigurationProperties + @Component 实现等价机制，
     * 各子模块独立管理自身配置，不依赖中心注册器。
     */
    public static void register(String key, Object config) {
        // Spring 环境下通过 @ConfigurationProperties 自动绑定
        // 此处提供手动注册入口以兼容 Go 的 init() 模式
    }
}
