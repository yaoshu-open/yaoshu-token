package yaoshu.token.relay;

import yaoshu.token.pojo.dto.ErrorCode;
import yaoshu.token.pojo.dto.ErrorType;
import yaoshu.token.pojo.dto.RelayException;
import yaoshu.token.pojo.dto.OpenAIError;
import yaoshu.token.relay.common.OverrideUtils;

/**
 * 参数覆写错误工厂  * <p>
 * 把 {@link OverrideUtils.ParamOverrideReturnError}（渠道配置的 return_error 操作抛出）
 * 转换为携带 OpenAI 兼容错误结构的 {@link RelayException}。
 */
public final class ParamOverrideError {

    private ParamOverrideError() {
    }

    /**
     * 从参数覆写错误创建 RelayException。      * <p>
     * 若 err 链中存在 ParamOverrideReturnError，则按其携带的 statusCode/code/type/message
     * 构造 OpenAI 兼容错误；否则按通用 channel_param_override_invalid 错误处理。
     */
    public static RelayException newAPIErrorFromParamOverride(Throwable err) {
        OverrideUtils.ParamOverrideReturnError fixedErr = asParamOverrideReturnError(err);
        if (fixedErr != null) {
            return buildFromParamOverride(fixedErr);
        }
        RelayException apiError = new RelayException(err.getMessage(), ErrorCode.CHANNEL_PARAM_OVERRIDE_INVALID);
        apiError.setSkipRetry(true);
        return apiError;
    }

    /** 沿异常 cause 链查找 ParamOverrideReturnError*/
    private static OverrideUtils.ParamOverrideReturnError asParamOverrideReturnError(Throwable err) {
        Throwable cur = err;
        while (cur != null) {
            if (cur instanceof OverrideUtils.ParamOverrideReturnError e) {
                return e;
            }
            cur = cur.getCause();
        }
        return null;
    }

    /** 参数覆写错误（ParamOverrideReturnError 转换产物） */
    private static RelayException buildFromParamOverride(OverrideUtils.ParamOverrideReturnError err) {
        int statusCode = err.getStatusCode();
        if (statusCode < 100 || statusCode > 511) {
            statusCode = 400;
        }
        String code = err.getErrorCode();
        if (code == null || code.trim().isEmpty()) {
            code = ErrorCode.INVALID_REQUEST;
        }
        String type = err.getErrorType();
        if (type == null || type.trim().isEmpty()) {
            type = "invalid_request_error";
        }
        String message = err.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "request blocked by param override";
        }

        RelayException apiError = new RelayException(message, code);
        apiError.setErrorType(ErrorType.OPENAI_ERROR.getValue());
        apiError.setStatusCode(statusCode);
        apiError.setSkipRetry(err.isSkipRetry());
        OpenAIError openAIError = new OpenAIError();
        openAIError.setMessage(message);
        openAIError.setType(type);
        openAIError.setCode(code);
        apiError.setRelayError(openAIError);
        return apiError;
    }
}
