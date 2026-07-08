package yaoshu.token.relay;

import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.spi.RelayRequestInterceptor;

/**
 * 请求拦截默认实现 — 空实现（透传所有行为）。
 * <p>
 * 可通过 SPI 扩展点覆盖此 Bean 实现自定义请求拦截（如 User-Agent 智能替换）。
 */
public class NoOpRelayRequestInterceptor implements RelayRequestInterceptor {

    @Override
    public void preRequest(RelayInfo info) {
        // 开源版不拦截请求
    }

    @Override
    public void postResponse(RelayInfo info, Exception error) {
        // 开源版不拦截响应
    }
}
