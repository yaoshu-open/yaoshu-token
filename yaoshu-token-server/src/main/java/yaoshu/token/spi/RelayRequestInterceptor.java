package yaoshu.token.spi;

import yaoshu.token.relay.common.RelayInfo;

/**
 * 请求拦截 — 请求发往上游前/响应返回后调用的拦截钩子。
 * 开源默认实现为空实现（透传所有行为）。
 */
public interface RelayRequestInterceptor {

    /** 请求发往上游前调用，可修改请求头/请求体 */
    void preRequest(RelayInfo info);

    /** 上游响应返回后调用（含异常场景），用于日志/指标记录 */
    void postResponse(RelayInfo info, Exception error);
}
