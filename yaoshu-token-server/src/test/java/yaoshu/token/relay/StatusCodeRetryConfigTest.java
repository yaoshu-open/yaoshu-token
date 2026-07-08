package yaoshu.token.relay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import yaoshu.token.constant.StatusCodeRetryConfig;

/**
 * StatusCodeRetryConfig 单元测试 — 覆盖 Go AutomaticRetryStatusCodeRanges / alwaysSkipRetry 全量规则。
 * <p>
 * 不依赖 Spring 容器，纯逻辑验证。
 */
class StatusCodeRetryConfigTest {

    // ======================== shouldRetryByStatusCode ========================

    @Test
    void shouldRetry_1xx() {
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(100)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(199)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(0)).isFalse();  // <100
    }

    @Test
    void shouldRetry_2xx_noRetry() {
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(200)).isFalse();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(201)).isFalse();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(299)).isFalse();
    }

    @Test
    void shouldRetry_3xx() {
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(300)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(302)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(399)).isTrue();
    }

    @Test
    void shouldRetry_4xx_excluding400() {
        // 400 Bad Request — 不可重试
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(400)).isFalse();
        // 408 Request Timeout — 可重试（换渠道重试）
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(408)).isTrue();
        // 401-407 可重试
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(401)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(404)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(407)).isTrue();
        // 409-499 可重试
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(409)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(429)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(499)).isTrue();
    }

    @Test
    void shouldRetry_5xx_excluding504and524() {
        // 504 Gateway Timeout — 跳过
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(504)).isFalse();
        // 524 A Timeout Occurred — 跳过
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(524)).isFalse();
        // 500-503 可重试
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(500)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(503)).isTrue();
        // 505-523 可重试
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(505)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(523)).isTrue();
        // 525-599 可重试
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(525)).isTrue();
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(599)).isTrue();
    }

    @Test
    void shouldRetry_statusCode600_above_599() {
        // >599 — 异常状态码，在 Go shouldRetry 中单独处理（<100 || >599 → true）
        // 但 StatusCodeRetryConfig.shouldRetryByStatusCode 不覆盖此分支（由 shouldRetry 调用方处理）
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(600)).isFalse();
    }

    // ======================== isAlwaysSkipRetryStatusCode ========================

    @Test
    void alwaysSkipRetryStatusCodes() {
        assertThat(StatusCodeRetryConfig.isAlwaysSkipRetryStatusCode(504)).isTrue();
        assertThat(StatusCodeRetryConfig.isAlwaysSkipRetryStatusCode(524)).isTrue();
        assertThat(StatusCodeRetryConfig.isAlwaysSkipRetryStatusCode(404)).isFalse();
        assertThat(StatusCodeRetryConfig.isAlwaysSkipRetryStatusCode(500)).isFalse();
    }

    // ======================== isAlwaysSkipRetryErrorCode ========================

    @Test
    void alwaysSkipRetryErrorCode_badResponseBody() {
        assertThat(StatusCodeRetryConfig.isAlwaysSkipRetryErrorCode("bad_response_body")).isTrue();
    }

    @Test
    void alwaysSkipRetryErrorCode_otherCodes_shouldNotSkip() {
        assertThat(StatusCodeRetryConfig.isAlwaysSkipRetryErrorCode("do_request_failed")).isFalse();
        assertThat(StatusCodeRetryConfig.isAlwaysSkipRetryErrorCode("get_channel_failed")).isFalse();
        assertThat(StatusCodeRetryConfig.isAlwaysSkipRetryErrorCode(null)).isFalse();
        assertThat(StatusCodeRetryConfig.isAlwaysSkipRetryErrorCode("")).isFalse();
    }

    // ======================== 边缘场景 ========================

    @Test
    void shouldRetryByStatusCode_negativeValues() {
        // -1 不在任何范围内 → 不重试
        assertThat(StatusCodeRetryConfig.shouldRetryByStatusCode(-1)).isFalse();
    }
}
