package yaoshu.token.constant;

import ai.yue.library.base.view.ResultCode;
import yaoshu.token.pojo.dto.ErrorCode;

/**
 * 渠道测试错误类型状态码（yue-library {@link ResultCode} 扩展，code &gt; 600）
 * <p>
 * 将 relay 领域的 {@code ErrorCode} 字符串映射为框架原生的数字状态码，
 * 前端按 {@code Result.code} 字段精确分支，替代原 Go 版响应体内的 {@code errorCode} 字符串匹配。
 */
public enum ChannelTestResultCode implements ResultCode {

    /** 模型价格未配置 */
    MODEL_PRICE_ERROR(601, "模型价格未配置"),
    /** API 类型适配器不存在 */
    INVALID_API_TYPE(602, "API 类型无效"),
    /** 渠道 Key 无效 */
    CHANNEL_INVALID_KEY(603, "渠道 Key 无效"),
    /** 上游响应状态异常 */
    BAD_RESPONSE_STATUS(604, "上游响应状态异常"),
    /** 响应体格式错误 */
    BAD_RESPONSE_BODY(605, "响应体格式错误"),
    /** 空响应 */
    EMPTY_RESPONSE(606, "空响应"),
    /** 模型未找到 */
    MODEL_NOT_FOUND(607, "模型未找到");

    private final int code;
    private final String msg;

    ChannelTestResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    /**
     * 将 relay 领域 errorCode 映射为 ChannelTestResultCode
     *
     * @param errorCode relay 领域错误码字符串（如 {@code "model_price_error"}）
     * @return 对应的 ChannelTestResultCode，无映射时返回 null（调用方走通用 600）
     */
    public static ChannelTestResultCode fromErrorCode(String errorCode) {
        if (errorCode == null) {
            return null;
        }
        return switch (errorCode) {
            case ErrorCode.MODEL_PRICE_ERROR -> MODEL_PRICE_ERROR;
            case ErrorCode.INVALID_API_TYPE -> INVALID_API_TYPE;
            case ErrorCode.CHANNEL_INVALID_KEY -> CHANNEL_INVALID_KEY;
            case ErrorCode.BAD_RESPONSE_STATUS_CODE -> BAD_RESPONSE_STATUS;
            case ErrorCode.BAD_RESPONSE, ErrorCode.BAD_RESPONSE_BODY -> BAD_RESPONSE_BODY;
            case ErrorCode.EMPTY_RESPONSE -> EMPTY_RESPONSE;
            case ErrorCode.MODEL_NOT_FOUND -> MODEL_NOT_FOUND;
            default -> null;
        };
    }
}
