package yaoshu.token.middleware;

import yaoshu.token.constant.EnvConstants;
import yaoshu.token.service.SysLogService;

/**
 * 请求体大小限制计算  */
public final class RequestBodyLimitFilter {

    private RequestBodyLimitFilter() {
    }

    private static final int DEFAULT_ANONYMOUS_LIMIT_KB = 512;

    /** 获取匿名请求体大小限制（字节） */
    public static long getAnonymousRequestBodyLimitBytes() {
        int limitKB = EnvConstants.anonymousRequestBodyLimitKB;
        if (limitKB < 0) {
            limitKB = DEFAULT_ANONYMOUS_LIMIT_KB;
        }
        return (long) limitKB << 10; // KB → bytes
    }
}
