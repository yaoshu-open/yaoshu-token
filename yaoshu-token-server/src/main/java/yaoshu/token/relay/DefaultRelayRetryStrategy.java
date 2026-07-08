package yaoshu.token.relay;

import yaoshu.token.constant.StatusCodeRetryConfig;
import yaoshu.token.pojo.dto.RelayException;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.spi.RelayRetryStrategy;

/**
 * 重试策略默认实现 — 与 Go {@code StatusCodeRetryConfig.shouldRetryByStatusCode()} 等价。
 * <p>
 * 决策链（前置守卫由调用方处理——null/remainingRetries/affinity/specific_channel_id）：
 * <ol>
 *   <li>ChannelError → 重试</li>
 *   <li>SkipRetry 标记 → 不重试</li>
 *   <li>2xx → 不重试</li>
 *   <li>&lt;100 或 &gt;599 → 重试</li>
 *   <li>AlwaysSkipRetryCode → 不重试</li>
 *   <li>StatusCodeRetryConfig 范围匹配 → 决策</li>
 * </ol>
 */
public class DefaultRelayRetryStrategy implements RelayRetryStrategy {

    @Override
    public boolean shouldRetry(RelayInfo info, Exception error, int remainingRetries) {
        if (!(error instanceof RelayException apiError)) {
            return false;
        }

        // ChannelError → 重试
        if (apiError.getErrorCode() != null && apiError.getErrorCode().startsWith("channel:")) {
            return true;
        }

        // SkipRetry 标记
        if (apiError.isSkipRetry()) {
            return false;
        }

        int code = apiError.getStatusCode();
        if (code >= 200 && code < 300) {
            return false;
        }
        if (code < 100 || code > 599) {
            return true;
        }

        // Go AlwaysSkipRetryCode 检查
        if (StatusCodeRetryConfig.isAlwaysSkipRetryErrorCode(apiError.getErrorCode())) {
            return false;
        }

        return StatusCodeRetryConfig.shouldRetryByStatusCode(code);
    }
}
