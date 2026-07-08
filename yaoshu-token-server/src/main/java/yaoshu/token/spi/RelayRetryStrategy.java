package yaoshu.token.spi;

import yaoshu.token.relay.common.RelayInfo;

/**
 * 重试策略 — 判断错误发生后是否应继续重试。
 * 开源默认实现与 Go {@code StatusCodeRetryConfig.shouldRetryByStatusCode()} 等价。
 */
public interface RelayRetryStrategy {

    /**
     * @param info              relay 上下文
     * @param error             当前错误（可能为 null）
     * @param remainingRetries  剩余重试次数
     * @return true 继续重试，false 停止
     */
    boolean shouldRetry(RelayInfo info, Exception error, int remainingRetries);
}
