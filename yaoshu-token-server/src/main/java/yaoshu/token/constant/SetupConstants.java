package yaoshu.token.constant;

/**
 * 系统初始化状态常量  */
public final class SetupConstants {

    private SetupConstants() {
    }

    /** 系统是否已完成初始化设置（运行时缓存，真实判据查 setups 表） */
    public static volatile boolean setup;

    // ======================== 初始化模式与环境变量（向前规范，不走 yml，便于 Docker -e 注入） ========================

    /** 环境变量：初始化模式（auto / interactive） */
    public static final String ENV_INIT_MODE = "YAOSHU_INIT_MODE";

    /** 环境变量：auto 模式 root 用户名 */
    public static final String ENV_INIT_ROOT_USERNAME = "YAOSHU_INIT_ROOT_USERNAME";

    /** 环境变量：auto 模式 root 密码（生产建议预置，避免明文进日志） */
    public static final String ENV_INIT_ROOT_PASSWORD = "YAOSHU_INIT_ROOT_PASSWORD";

    /** 模式：启动自动初始化（默认） */
    public static final String MODE_AUTO = "auto";

    /** 模式：前端 SetupWizard 交互初始化 */
    public static final String MODE_INTERACTIVE = "interactive";

    /** 默认 root 用户名 */
    public static final String DEFAULT_ROOT_USERNAME = "root";

    /** 随机密码长度 */
    public static final int RANDOM_PASSWORD_LENGTH = 12;
}
