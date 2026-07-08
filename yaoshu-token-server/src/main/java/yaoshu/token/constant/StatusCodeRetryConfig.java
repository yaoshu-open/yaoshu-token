package yaoshu.token.constant;

import java.util.Set;

/**
 * 状态码重试范围配置  * <p>
 * 定义哪些 HTTP 状态码范围应触发重试、哪些状态码/错误码应跳过重试。
 * 与 Go AutomaticRetryStatusCodeRanges / alwaysSkipRetry 保持 1:1 对应。
 */
public final class StatusCodeRetryConfig {

    private StatusCodeRetryConfig() {}

    // ======================== 重试状态码范围 ======================== 
    private static final int[][] RETRY_RANGES = {
            {100, 199},   // 1xx 信息性
            {300, 399},   // 3xx 重定向
            {401, 408},   // 4xx（排除 400；408 请求超时换渠道重试）
            {409, 499},
            {500, 503},   // 5xx（排除 504/524）
            {505, 523},
            {525, 599},
    };

    // ======================== 始终跳过重试的状态码 ======================== 
    /** 504 Gateway Timeout / 524 A Timeout Occurred — 上游超时，换渠道重试有效 */
    private static final Set<Integer> ALWAYS_SKIP_STATUS_CODES = Set.of(504, 524);

    /** 始终跳过重试的错误码 */
    private static final Set<String> ALWAYS_SKIP_ERROR_CODES = Set.of(
            yaoshu.token.pojo.dto.ErrorCode.BAD_RESPONSE_BODY
    );

    // ======================== 公共方法 ========================

    /**
     * 判断指定状态码是否在重试范围内      */
    public static boolean shouldRetryByStatusCode(int code) {
        if (ALWAYS_SKIP_STATUS_CODES.contains(code)) {
            return false;
        }
        for (int[] range : RETRY_RANGES) {
            if (code >= range[0] && code <= range[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断错误码是否应始终跳过重试      */
    public static boolean isAlwaysSkipRetryErrorCode(String errorCode) {
        return errorCode != null && ALWAYS_SKIP_ERROR_CODES.contains(errorCode);
    }

    /**
     * 判断状态码是否应始终跳过重试      */
    public static boolean isAlwaysSkipRetryStatusCode(int code) {
        return ALWAYS_SKIP_STATUS_CODES.contains(code);
    }
}
