package yaoshu.token.constant;

import lombok.Getter;

/**
 * 环境变量名常量  * <p>
 * 这些值在 Go 中是从环境变量/配置文件读入的包级变量，翻译为 Java 时
 * 设计为静态可配置字段（由配置加载阶段赋值），而非编译期常量。
 */
@Getter
public final class EnvConstants {

    private EnvConstants() {
    }

    /** 流式请求超时 */
    public static int streamingTimeout;
    /** Dify 调试模式 */
    public static boolean difyDebug;
    /** 最大文件下载大小（MB） */
    public static int maxFileDownloadMB;
    /** 流扫描器最大缓冲区大小（MB） */
    public static int streamScannerMaxBufferMB;
    /** 强制流式选项 */
    public static boolean forceStreamOption;
    /** 是否计数 Token */
    public static boolean countToken;
    /** 是否获取媒体 Token */
    public static boolean getMediaToken;
    /** 非流式请求是否也获取媒体 Token */
    public static boolean getMediaTokenNotStream;
    /** 是否更新任务 */
    public static boolean updateTask;
    /** 最大请求体大小（MB） */
    public static int maxRequestBodyMB;
    /** 匿名请求体限制（KB） */
    public static int anonymousRequestBodyLimitKB;
    /** Azure 默认 API 版本 */
    public static String azureDefaultAPIVersion;
    /** 通知限制次数 */
    public static int notifyLimitCount;
    /** 通知限制时间间隔（分钟） */
    public static int notificationLimitDurationMinute;
    /** 是否生成默认 Token */
    public static boolean generateDefaultToken;
    /** 是否启用错误日志 */
    public static boolean errorLogEnabled;
    /** 任务查询限制数量 */
    public static int taskQueryLimit;
    /** 任务超时（分钟） */
    public static int taskTimeoutMinutes;

    /** 任务价格补丁：Sora 等任务类模型的额外计价规则 */
    public static String[] taskPricePatches;

    /** 可信任的重定向域名列表（支持子域名匹配） */
    public static String[] trustedRedirectDomains;
}
