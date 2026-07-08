package yaoshu.token.constant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 全局公共常量与运行时配置  * <p>
 * 注意：部分环境相关的配置变量位于 {@link EnvConstants}  */
public final class CommonConstants {

    private CommonConstants() {
    }

    // ======================== 系统元数据 ========================

    /** 启动时间戳（Unix 秒） */
    public static long startTime;
    /** 版本号（构建时自动替换） */
    public static String version = "v0.0.0";
    /** 系统名称 */
    public static String systemName = "New API";
    /** 页脚文案 */
    public static String footer = "";
    /** Logo 路径 */
    public static String logo = "";
    /** 充值链接 */
    public static String topUpLink = "";

    // ======================== 主题管理（线程安全） ========================

    private static final AtomicReference<String> themeRef = new AtomicReference<>("classic");

    /** 获取当前主题 */
    public static String getTheme() {
        return themeRef.get();
    }

    /**
     * 设置前端主题，仅接受 "default" 和 "classic"，其他值静默忽略
     */
    public static void setTheme(String theme) {
        if ("default".equals(theme) || "classic".equals(theme)) {
            themeRef.set(theme);
        }
    }

    /**
     * 当日志主题为 "default" 时，将旧版 /console/* 路径重写为新路径
     */
    public static String themeAwarePath(String suffix) {
        if (!"default".equals(getTheme())) {
            return suffix;
        }
        if (suffix.startsWith("/console/topup")) {
            return suffix.replaceFirst("/console/topup", "/wallet");
        }
        if (suffix.startsWith("/console/log")) {
            return suffix.replaceFirst("/console/log", "/usage-logs");
        }
        if (suffix.startsWith("/console/personal")) {
            return suffix.replaceFirst("/console/personal", "/profile");
        }
        return suffix;
    }

    // ======================== 功能开关 ========================

    /** 每单位配额对应的金额比例因子（$0.002 / 1K tokens → 500 * 1000） */
    public static double quotaPerUnit = 500_000.0;

    /** 保留旧变量以兼容历史逻辑，实际展示由 general_setting.quota_display_type 控制 */
    public static boolean displayInCurrencyEnabled = true;
    public static boolean displayTokenStatEnabled = true;
    public static boolean drawingEnabled = true;
    public static boolean taskEnabled = true;
    public static boolean dataExportEnabled = true;
    /** 数据导出间隔（分钟） */
    public static int dataExportInterval = 5;
    /** 数据导出默认时间 */
    public static String dataExportDefaultTime = "hour";
    /** 侧边栏默认折叠 */
    public static boolean defaultCollapseSidebar = false;

    // ======================== 认证开关 ========================

    public static boolean passwordLoginEnabled = true;
    public static boolean passwordRegisterEnabled = true;
    public static boolean emailVerificationEnabled = false;
    public static boolean gitHubOAuthEnabled = false;
    public static boolean linuxDOOAuthEnabled = false;
    public static boolean weChatAuthEnabled = false;
    public static boolean telegramOAuthEnabled = false;
    public static boolean turnstileCheckEnabled = false;
    public static boolean registerEnabled = true;

    // ======================== 邮箱域限制 ========================

    /** 是否启用邮箱域名限制 */
    public static boolean emailDomainRestrictionEnabled = false;
    /** 是否启用邮箱别名限制 */
    public static boolean emailAliasRestrictionEnabled = false;
    /** 邮箱域名白名单 */
    public static List<String> emailDomainWhitelist = List.of(
            "gmail.com", "163.com", "126.com", "qq.com",
            "outlook.com", "hotmail.com", "icloud.com", "yahoo.com", "foxmail.com"
    );
    /** 邮箱登录认证 SMTP 服务商列表 */
    public static List<String> emailLoginAuthServerList = List.of(
            "smtp.sendcloud.net", "smtp.azurecomm.net"
    );

    // ======================== 调试与缓存 ========================

    public static boolean debugEnabled;
    public static boolean memoryCacheEnabled;
    public static boolean logConsumeEnabled = true;

    // ======================== TLS 配置 ========================

    /** 是否跳过 TLS 证书验证（调试用） */
    public static boolean tlsInsecureSkipVerify;

    // ======================== SMTP 配置 ========================

    public static String smtpServer = "";
    public static int smtpPort = 587;
    public static boolean smtpSslEnabled = false;
    public static boolean smtpForceAuthLogin = false;
    public static String smtpAccount = "";
    public static String smtpFrom = "";
    public static String smtpToken = "";

    // ======================== OAuth 客户端配置 ========================

    public static String gitHubClientId = "";
    public static String gitHubClientSecret = "";
    public static String linuxDOClientId = "";
    public static String linuxDOClientSecret = "";
    public static int linuxDOMinimumTrustLevel;

    // ======================== 微信配置 ========================

    public static String weChatServerAddress = "";
    public static String weChatServerToken = "";
    public static String weChatAccountQRCodeImageURL = "";

    // ======================== Turnstile 验证码 ========================

    public static String turnstileSiteKey = "";
    public static String turnstileSecretKey = "";

    // ======================== Telegram 配置 ========================

    public static String telegramBotToken = "";
    public static String telegramBotName = "";

    // ======================== 配额配置 ========================

    public static int quotaForNewUser;
    public static int quotaForInviter;
    public static int quotaForInvitee;
    /** 渠道自动禁用阈值 */
    public static double channelDisableThreshold = 5.0;
    /** 是否启用渠道自动禁用 */
    public static boolean automaticDisableChannelEnabled;
    /** 是否启用渠道自动启用 */
    public static boolean automaticEnableChannelEnabled;
    /** 配额提醒阈值 */
    public static int quotaRemindThreshold = 1000;
    /** 预消费配额 */
    public static int preConsumedQuota = 500;

    // ======================== 重试与节点 ========================

    public static int retryTimes;

    /** 是否为主节点 */
    public static boolean isMasterNode;

    /**
     * 节点名称，从 NODE_NAME 环境变量读取；
     * 用于审计日志中标识节点身份，在容器/K8s 部署时比自动探测到的容器内网 IP 更具可读性。
     */
    public static String nodeName = "";

    // ======================== 请求配置 ========================

    /** 请求间隔（秒） */
    public static int requestInterval;
    /** 同步频率（秒） */
    public static int syncFrequency;

    // ======================== 批量更新 ========================

    public static boolean batchUpdateEnabled;
    public static int batchUpdateInterval;

    // ======================== Relay 超时与连接池 ========================

    /** 上游中继超时（秒） */
    public static int relayTimeout;
    /** 上游中继空闲连接超时（秒） */
    public static int relayIdleConnTimeout;
    /** 上游最大空闲连接数 */
    public static int relayMaxIdleConns;
    /** 上游每个 Host 最大空闲连接数 */
    public static int relayMaxIdleConnsPerHost;

    // ======================== AI 安全设置 ========================

    /** Gemini 安全设置 JSON */
    public static String geminiSafetySetting;
    /** Cohere 安全模式：NONE/CONTEXTUAL/STRICT */
    public static String cohereSafetySetting;

    // ======================== 请求头 Key 常量 ========================

    public static final String REQUEST_ID_KEY = "X-Oneapi-Request-Id";
    public static final String UPSTREAM_REQUEST_ID_KEY = "X-Upstream-Request-Id";

    // ======================== 角色常量 ========================

    public static final int ROLE_GUEST_USER = 0;
    public static final int ROLE_COMMON_USER = 1;
    public static final int ROLE_ADMIN_USER = 10;
    public static final int ROLE_ROOT_USER = 100;

    /** 校验角色值是否合法 */
    public static boolean isValidRole(int role) {
        return role == ROLE_GUEST_USER || role == ROLE_COMMON_USER
                || role == ROLE_ADMIN_USER || role == ROLE_ROOT_USER;
    }

    // ======================== 文件/图片上传权限 ========================

    /** 文件上传最低角色要求 */
    public static int fileUploadPermission = ROLE_GUEST_USER;
    /** 文件下载最低角色要求 */
    public static int fileDownloadPermission = ROLE_GUEST_USER;
    /** 图片上传最低角色要求 */
    public static int imageUploadPermission = ROLE_GUEST_USER;
    /** 图片下载最低角色要求 */
    public static int imageDownloadPermission = ROLE_GUEST_USER;

    // ======================== 用户状态常量 ========================

    /** 用户启用（不用 0，0 是默认值） */
    public static final int USER_STATUS_ENABLED = 1;
    /** 用户禁用 */
    public static final int USER_STATUS_DISABLED = 2;

    // ======================== Token 状态常量 ========================

    /** Token 启用 */
    public static final int TOKEN_STATUS_ENABLED = 1;
    /** Token 禁用 */
    public static final int TOKEN_STATUS_DISABLED = 2;
    /** Token 已过期 */
    public static final int TOKEN_STATUS_EXPIRED = 3;
    /** Token 已耗尽 */
    public static final int TOKEN_STATUS_EXHAUSTED = 4;

    // ======================== 兑换码状态常量 ========================

    /** 兑换码启用 */
    public static final int REDEMPTION_CODE_STATUS_ENABLED = 1;
    /** 兑换码禁用 */
    public static final int REDEMPTION_CODE_STATUS_DISABLED = 2;
    /** 兑换码已使用 */
    public static final int REDEMPTION_CODE_STATUS_USED = 3;

    // ======================== 渠道状态常量 ========================

    /** 渠道状态未知 */
    public static final int CHANNEL_STATUS_UNKNOWN = 0;
    /** 渠道启用 */
    public static final int CHANNEL_STATUS_ENABLED = 1;
    /** 渠道手动禁用 */
    public static final int CHANNEL_STATUS_MANUALLY_DISABLED = 2;
    /** 渠道自动禁用 */
    public static final int CHANNEL_STATUS_AUTO_DISABLED = 3;

    // ======================== 充值状态常量 ========================

    public static final String TOP_UP_STATUS_PENDING = "pending";
    public static final String TOP_UP_STATUS_SUCCESS = "success";
    public static final String TOP_UP_STATUS_FAILED = "failed";
    public static final String TOP_UP_STATUS_EXPIRED = "expired";

    // ======================== 全局限流配置 ========================

    /** 所有限流 Duration 的单位为秒，不应大于 RateLimitKeyExpirationDuration */

    public static boolean globalApiRateLimitEnable;
    public static int globalApiRateLimitNum;
    public static long globalApiRateLimitDuration;

    public static boolean globalWebRateLimitEnable;
    public static int globalWebRateLimitNum;
    public static long globalWebRateLimitDuration;

    public static boolean criticalRateLimitEnable;
    public static int criticalRateLimitNum = 20;
    /** 关键操作限流 Duration（秒） */
    public static long criticalRateLimitDuration = 20 * 60;

    public static int uploadRateLimitNum = 10;
    /** 上传限流 Duration（秒） */
    public static long uploadRateLimitDuration = 60;

    public static int downloadRateLimitNum = 10;
    /** 下载限流 Duration（秒） */
    public static long downloadRateLimitDuration = 60;

    /** 每用户搜索限流（认证后，按用户 ID 分 Key） */
    public static boolean searchRateLimitEnable = true;
    public static int searchRateLimitNum = 10;
    /** 搜索限流 Duration（秒） */
    public static long searchRateLimitDuration = 60;

    /** 限流 Key 过期时间 */
    public static Duration rateLimitKeyExpirationDuration = Duration.ofMinutes(20);

    // ======================== OptionMap（选项配置缓存） ========================

    /** 选项配置内存缓存 */
    public static Map<String, String> optionMap;
    public static final ReentrantReadWriteLock optionMapLock = new ReentrantReadWriteLock();

    // ======================== 分页与最大条目 ========================

    public static int itemsPerPage = 10;
    public static int maxRecentItems = 1000;

    // ======================== Session/加密密钥 ========================

    /** Session 密钥（由 uuid 生成，与 Go 行为一致由初始化时代码赋值） */
    public static String sessionSecret;
    /** 加密密钥 */
    public static String cryptoSecret;
}
