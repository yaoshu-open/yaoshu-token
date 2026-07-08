package yaoshu.token.spi;

import org.junit.jupiter.api.Test;
import yaoshu.token.pojo.dto.RelayException;
import yaoshu.token.relay.DefaultRelayRetryStrategy;
import yaoshu.token.relay.common.RelayInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultRelayRetryStrategy 单元测试 — 覆盖状态码重试决策链。
 */
class DefaultRelayRetryStrategyTest {

    private final DefaultRelayRetryStrategy strategy = new DefaultRelayRetryStrategy();

    @Test
    void shouldRetryOnChannelError() {
        RelayInfo info = new RelayInfo();
        RelayException error = new RelayException("disabled", "channel:disabled");
        error.setStatusCode(403);
        assertThat(strategy.shouldRetry(info, error, 2)).isTrue();
    }

    @Test
    void shouldNotRetryOnSkipRetry() {
        RelayInfo info = new RelayInfo();
        RelayException error = new RelayException("skip", "test");
        error.setStatusCode(503);
        error.setSkipRetry(true);
        assertThat(strategy.shouldRetry(info, error, 2)).isFalse();
    }

    @Test
    void shouldNotRetryOn2xx() {
        RelayInfo info = new RelayInfo();
        RelayException error = new RelayException("ok", "ok");
        error.setStatusCode(200);
        assertThat(strategy.shouldRetry(info, error, 2)).isFalse();

        error.setStatusCode(299);
        assertThat(strategy.shouldRetry(info, error, 2)).isFalse();
    }

    @Test
    void shouldRetryOnAbnormalCode() {
        RelayInfo info = new RelayInfo();
        RelayException error = new RelayException("abnormal", "abnormal");
        error.setStatusCode(50);
        assertThat(strategy.shouldRetry(info, error, 2)).isTrue();

        error.setStatusCode(700);
        assertThat(strategy.shouldRetry(info, error, 2)).isTrue();
    }

    @Test
    void shouldNotRetryOnNonRelayException() {
        RelayInfo info = new RelayInfo();
        assertThat(strategy.shouldRetry(info, new RuntimeException("generic"), 2)).isFalse();
    }

    // ======================== 状态码范围覆盖 ========================

    @Test
    void shouldRetry1xx() {
        assertThat(strategy.shouldRetry(new RelayInfo(),
                errorWithCode(102), 2)).isTrue();
    }

    @Test
    void shouldRetry3xx() {
        assertThat(strategy.shouldRetry(new RelayInfo(),
                errorWithCode(302), 2)).isTrue();
    }

    @Test
    void shouldNotRetry400() {
        assertThat(strategy.shouldRetry(new RelayInfo(),
                errorWithCode(400), 2)).isFalse();
    }

    @Test
    void shouldRetry401() {
        assertThat(strategy.shouldRetry(new RelayInfo(),
                errorWithCode(401), 2)).isTrue();
    }

    @Test
    void shouldNotRetry504() {
        assertThat(strategy.shouldRetry(new RelayInfo(),
                errorWithCode(504), 2)).isFalse();
    }

    @Test
    void shouldNotRetry524() {
        assertThat(strategy.shouldRetry(new RelayInfo(),
                errorWithCode(524), 2)).isFalse();
    }

    @Test
    void shouldRetry500() {
        assertThat(strategy.shouldRetry(new RelayInfo(),
                errorWithCode(500), 2)).isTrue();
    }

    @Test
    void shouldRetry503() {
        assertThat(strategy.shouldRetry(new RelayInfo(),
                errorWithCode(503), 2)).isTrue();
    }

    @Test
    void shouldRetry408() {
        // 408 Request Timeout — 可重试（换渠道重试）
        assertThat(strategy.shouldRetry(new RelayInfo(),
                errorWithCode(408), 2)).isTrue();
    }

    private static RelayException errorWithCode(int code) {
        RelayException error = new RelayException("test-msg", "test-code");
        error.setStatusCode(code);
        return error;
    }
}
