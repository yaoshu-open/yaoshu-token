package yaoshu.token.pojo.dto;

import lombok.Getter;

/**
 * 错误类型枚举
 */
@Getter
public enum ErrorType {

    YAOSHU_TOKEN_ERROR("yaoshu_token_error"),
    OPENAI_ERROR("openai_error"),
    CLAUDE_ERROR("claude_error"),
    MIDJOURNEY_ERROR("midjourney_error"),
    GEMINI_ERROR("gemini_error"),
    RERANK_ERROR("rerank_error"),
    UPSTREAM_ERROR("upstream_error");

    private final String value;

    ErrorType(String value) {
        this.value = value;
    }
}
