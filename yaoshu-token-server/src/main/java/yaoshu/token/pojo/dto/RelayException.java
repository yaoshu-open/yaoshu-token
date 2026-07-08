package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Getter;
import lombok.Setter;

/**
 * Relay 中转核心错误类型
 *
 * RelayException 携带丰富的上下文信息（错误码、类型、HTTP 状态码、元数据），
 * 翻译为 Java RuntimeException 保留这些信息。
 */
@Getter
@Setter
public class RelayException extends RuntimeException {

    private String errorCode;
    private String errorType;
    private int statusCode;
    private Object relayError;
    private boolean skipRetry;
    private boolean recordErrorLog;
    @JsonRawValue
    private String metadata;

    public RelayException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = ErrorType.YAOSHU_TOKEN_ERROR.getValue();
        this.statusCode = 500;
        this.recordErrorLog = true;
    }

    public RelayException(Throwable cause, String errorCode) {
        super(cause.getMessage(), cause);
        this.errorCode = errorCode;
        this.errorType = ErrorType.YAOSHU_TOKEN_ERROR.getValue();
        this.statusCode = 500;
        this.recordErrorLog = true;  // 默认视为 true
    }

    // ——— Go SetMessage → 直接调用 setMessage/super 构造函数 ———

    // ——— Go ToOpenAIError ———
    public OpenAIError toOpenAIError() {
        OpenAIError result = new OpenAIError();
        if (ErrorType.OPENAI_ERROR.getValue().equals(errorType)) {
            if (relayError instanceof OpenAIError oaiError) {
                result = oaiError;
            }
        } else if (ErrorType.CLAUDE_ERROR.getValue().equals(errorType)) {
            if (relayError instanceof ClaudeError claudeError) {
                result.setMessage(getMessage());
                result.setType(claudeError.getType());
                result.setParam("");
                result.setCode(errorCode);
            }
        } else {
            result.setMessage(getMessage());
            result.setType(errorType);
            result.setParam("");
            result.setCode(errorCode);
        }
        if (!ErrorCode.COUNT_TOKEN_FAILED.equals(errorCode)) {
            result.setMessage(maskSensitiveInfo(result.getMessage()));
        }
        if (result.getMessage() == null || result.getMessage().isEmpty()) {
            result.setMessage(errorType);
        }
        return result;
    }

    // ——— Go ToClaudeError ———
    public ClaudeError toClaudeError() {
        ClaudeError result = new ClaudeError();
        if (ErrorType.OPENAI_ERROR.getValue().equals(errorType)) {
            if (relayError instanceof OpenAIError oaiError && oaiError.getCode() != null) {
                result.setMessage(getMessage());
                result.setType(String.valueOf(oaiError.getCode()));
            }
        } else if (ErrorType.CLAUDE_ERROR.getValue().equals(errorType)) {
            if (relayError instanceof ClaudeError claudeError) {
                result = claudeError;
            }
        } else {
            result.setMessage(getMessage());
            result.setType(errorType);
        }
        if (!ErrorCode.COUNT_TOKEN_FAILED.equals(errorCode)) {
            result.setMessage(maskSensitiveInfo(result.getMessage()));
        }
        if (result.getMessage() == null || result.getMessage().isEmpty()) {
            result.setMessage(errorType);
        }
        return result;
    }

    // ——— Go ErrorWithStatusCode ———
    public String errorWithStatusCode() {
        String msg = getMessage();
        if (statusCode == 0) {
            return msg;
        }
        if (msg == null || msg.isEmpty()) {
            return "status_code=" + statusCode;
        }
        return "status_code=" + statusCode + ", " + msg;
    }

    // ——— Go MaskSensitiveError ———
    public String maskSensitiveError() {
        if (getMessage() == null) {
            return errorCode;
        }
        if (ErrorCode.COUNT_TOKEN_FAILED.equals(errorCode)) {
            return getMessage();
        }
        return maskSensitiveInfo(getMessage());
    }

    // ——— Go MaskSensitiveErrorWithStatusCode ———
    public String maskSensitiveErrorWithStatusCode() {
        String msg = maskSensitiveError();
        if (statusCode == 0) {
            return msg;
        }
        if (msg == null || msg.isEmpty()) {
            return "status_code=" + statusCode;
        }
        return "status_code=" + statusCode + ", " + msg;
    }

    // ——— 敏感信息脱敏
    // Go 源码已归档删除，基于使用场景（错误消息+URL脱敏）实现
    private static final java.util.regex.Pattern API_KEY_PATTERN =
            java.util.regex.Pattern.compile("(sk-|Bearer\\s+)[A-Za-z0-9_\\-]{8,}", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern URL_CREDENTIAL_PATTERN =
            java.util.regex.Pattern.compile("(:\\/\\/[^:\\/\\s]+:)([^@\\s]+)(@)");

    private String maskSensitiveInfo(String input) {
        if (input == null) return null;
        // 脱敏 API Key / Bearer Token
        String result = API_KEY_PATTERN.matcher(input).replaceAll("$1***");
        // 脱敏 URL 中的密码凭证
        result = URL_CREDENTIAL_PATTERN.matcher(result).replaceAll("$1***$3");
        return result;
    }
}
