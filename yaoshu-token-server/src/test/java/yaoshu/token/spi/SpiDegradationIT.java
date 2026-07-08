package yaoshu.token.spi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import yaoshu.token.BaseIntegrationTest;
import yaoshu.token.pojo.dto.RelayException;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.relay.DefaultChannelHealthHandler;
import yaoshu.token.relay.DefaultChannelSelector;
import yaoshu.token.relay.DefaultRelayRetryStrategy;
import yaoshu.token.relay.NoOpRelayRequestInterceptor;
import yaoshu.token.relay.common.RelayInfo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPI 退化集成测试 — 验证仅含默认实现时 Bean 正常注册与工作。
 * <p>
 * 测试环境仅含开源默认实现，所有 SPI Bean 应正确退化为默认实现。
 */
class SpiDegradationIT extends BaseIntegrationTest {

    @Autowired
    private RelayRequestInterceptor relayRequestInterceptor;
    @Autowired
    private RelayRetryStrategy relayRetryStrategy;
    @Autowired
    private ChannelHealthHandler channelHealthHandler;
    @Autowired
    private ChannelSelector channelSelector;

    // ======================== Bean 注册验证 ========================

    @Test
    void shouldRegisterNoOpRelayRequestInterceptor() {
        assertThat(relayRequestInterceptor)
                .isNotNull()
                .isInstanceOf(NoOpRelayRequestInterceptor.class);
    }

    @Test
    void shouldRegisterDefaultRelayRetryStrategy() {
        assertThat(relayRetryStrategy)
                .isNotNull()
                .isInstanceOf(DefaultRelayRetryStrategy.class);
    }

    @Test
    void shouldRegisterDefaultChannelHealthHandler() {
        assertThat(channelHealthHandler)
                .isNotNull()
                .isInstanceOf(DefaultChannelHealthHandler.class);
    }

    @Test
    void shouldRegisterDefaultChannelSelector() {
        assertThat(channelSelector)
                .isNotNull()
                .isInstanceOf(DefaultChannelSelector.class);
    }

    // ======================== 退化行为验证 ========================

    @Test
    void noOpInterceptorShouldNotThrow() {
        RelayInfo info = new RelayInfo();
        // 不应抛异常
        relayRequestInterceptor.preRequest(info);
        relayRequestInterceptor.postResponse(info, null);
        relayRequestInterceptor.postResponse(info, new RuntimeException("test"));
    }

    @Test
    void defaultRetryStrategyShouldRetryOnServerError() {
        RelayInfo info = new RelayInfo();
        RelayException error = new RelayException("Internal Server Error",
                yaoshu.token.pojo.dto.ErrorCode.DO_REQUEST_FAILED);
        error.setStatusCode(503);

        assertThat(relayRetryStrategy.shouldRetry(info, error, 2))
                .as("503 应在重试范围内")
                .isTrue();
    }

    @Test
    void defaultRetryStrategyShouldNotRetryOn2xx() {
        RelayInfo info = new RelayInfo();
        RelayException error = new RelayException("OK?", "ok");
        error.setStatusCode(200);

        assertThat(relayRetryStrategy.shouldRetry(info, error, 2))
                .as("2xx 不应重试")
                .isFalse();
    }

    @Test
    void defaultRetryStrategyShouldNotRetryOn504() {
        RelayInfo info = new RelayInfo();
        RelayException error = new RelayException("Gateway Timeout", "timeout");
        error.setStatusCode(504);

        assertThat(relayRetryStrategy.shouldRetry(info, error, 2))
                .as("504 始终跳过重试")
                .isFalse();
    }

    @Test
    void defaultRetryStrategyShouldRetryOnChannelError() {
        RelayInfo info = new RelayInfo();
        RelayException error = new RelayException("Channel disabled",
                "channel:disabled");
        error.setStatusCode(403);

        assertThat(relayRetryStrategy.shouldRetry(info, error, 2))
                .as("channel: 前缀错误应重试")
                .isTrue();
    }

    @Test
    void defaultChannelSelectorShouldReturnFirst() {
        Channel ch1 = new Channel();
        ch1.setId(1);
        ch1.setName("ch-1");
        Channel ch2 = new Channel();
        ch2.setId(2);
        ch2.setName("ch-2");

        Channel selected = channelSelector.select(List.of(ch1, ch2), new RelayInfo());
        assertThat(selected).isNotNull();
        assertThat(selected.getId()).isEqualTo(1);
    }

    @Test
    void defaultChannelSelectorShouldReturnNullOnEmpty() {
        Channel selected = channelSelector.select(List.of(), new RelayInfo());
        assertThat(selected).isNull();
    }

    @Test
    void defaultChannelHealthHandlerShouldNotThrowOnNull() {
        // null info 或 null error 不应抛异常（防御性）
        channelHealthHandler.onChannelError(new RelayInfo(), null);
    }
}
