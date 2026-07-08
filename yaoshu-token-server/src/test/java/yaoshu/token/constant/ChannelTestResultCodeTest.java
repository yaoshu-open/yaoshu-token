package yaoshu.token.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ai.yue.library.base.view.ResultCode;
import yaoshu.token.pojo.dto.ErrorCode;

/**
 * ChannelTestResultCode 单元测试 — 验证 errorCode → ResultCode 映射正确性。
 * <p>
 * 不依赖 Spring 容器，纯逻辑验证。
 */
class ChannelTestResultCodeTest {

    @Test
    void fromErrorCode_modelPriceError() {
        ChannelTestResultCode rc = ChannelTestResultCode.fromErrorCode(ErrorCode.MODEL_PRICE_ERROR);
        assertThat(rc).isEqualTo(ChannelTestResultCode.MODEL_PRICE_ERROR);
        assertThat(rc.getCode()).isEqualTo(601);
    }

    @Test
    void fromErrorCode_invalidApiType() {
        ChannelTestResultCode rc = ChannelTestResultCode.fromErrorCode(ErrorCode.INVALID_API_TYPE);
        assertThat(rc).isEqualTo(ChannelTestResultCode.INVALID_API_TYPE);
        assertThat(rc.getCode()).isEqualTo(602);
    }

    @Test
    void fromErrorCode_channelInvalidKey() {
        ChannelTestResultCode rc = ChannelTestResultCode.fromErrorCode(ErrorCode.CHANNEL_INVALID_KEY);
        assertThat(rc).isEqualTo(ChannelTestResultCode.CHANNEL_INVALID_KEY);
        assertThat(rc.getCode()).isEqualTo(603);
    }

    @Test
    void fromErrorCode_badResponseStatus() {
        ChannelTestResultCode rc = ChannelTestResultCode.fromErrorCode(ErrorCode.BAD_RESPONSE_STATUS_CODE);
        assertThat(rc).isEqualTo(ChannelTestResultCode.BAD_RESPONSE_STATUS);
        assertThat(rc.getCode()).isEqualTo(604);
    }

    @Test
    void fromErrorCode_badResponsemapsToResponseBody() {
        // bad_response 和 bad_response_body 都映射到 BAD_RESPONSE_BODY(605)
        assertThat(ChannelTestResultCode.fromErrorCode(ErrorCode.BAD_RESPONSE))
                .isEqualTo(ChannelTestResultCode.BAD_RESPONSE_BODY);
        assertThat(ChannelTestResultCode.fromErrorCode(ErrorCode.BAD_RESPONSE_BODY))
                .isEqualTo(ChannelTestResultCode.BAD_RESPONSE_BODY);
    }

    @Test
    void fromErrorCode_emptyResponse() {
        ChannelTestResultCode rc = ChannelTestResultCode.fromErrorCode(ErrorCode.EMPTY_RESPONSE);
        assertThat(rc).isEqualTo(ChannelTestResultCode.EMPTY_RESPONSE);
        assertThat(rc.getCode()).isEqualTo(606);
    }

    @Test
    void fromErrorCode_modelNotFound() {
        ChannelTestResultCode rc = ChannelTestResultCode.fromErrorCode(ErrorCode.MODEL_NOT_FOUND);
        assertThat(rc).isEqualTo(ChannelTestResultCode.MODEL_NOT_FOUND);
        assertThat(rc.getCode()).isEqualTo(607);
    }

    @Test
    void fromErrorCode_null_returnsNull() {
        assertThat(ChannelTestResultCode.fromErrorCode(null)).isNull();
    }

    @Test
    void fromErrorCode_unknown_returnsNull() {
        // 未映射的 errorCode 返回 null，调用方走通用 600
        assertThat(ChannelTestResultCode.fromErrorCode("some_unknown_error")).isNull();
        assertThat(ChannelTestResultCode.fromErrorCode(ErrorCode.INSUFFICIENT_USER_QUOTA)).isNull();
    }

    @Test
    void allCodes_areAbove600() {
        // ResultCode 规范：自定义 code > 600
        for (ChannelTestResultCode rc : ChannelTestResultCode.values()) {
            assertThat(rc.getCode()).isGreaterThan(600);
            assertThat(rc.getMsg()).isNotBlank();
        }
    }

    @Test
    void implementsResultCode() {
        // 验证枚举实现 yue-library ResultCode 接口
        ResultCode rc = ChannelTestResultCode.MODEL_PRICE_ERROR;
        assertThat(rc.getCode()).isEqualTo(601);
        assertThat(rc.getMsg()).isNotBlank();
    }
}
