package yaoshu.token.config.operation;

/**
 * 运营设置聚合器  * <p>
 * 作为所有运营子设置的注册与访问入口。
 */
public final class OperationSettingConfig {

    private OperationSettingConfig() {
    }

    /** 注册全局配置对象（由 Spring 启动时调用） */
    public static void register() {
        // 运营设置注册——所有子设置通过各自的 static 字段和 Config 类访问
        // Go 使用 config.GlobalConfig.Register()，Java 通过 Spring Bean 自动装配
    }
}
