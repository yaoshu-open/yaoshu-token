package yaoshu.token.constant;

import lombok.Getter;

/**
 * 端点类型枚举  */
@Getter
public enum EndpointTypeEnum {

    OPENAI("openai"),
    OPENAI_RESPONSE("openai-response"),
    OPENAI_RESPONSE_COMPACT("openai-response-compact"),
    ANTHROPIC("anthropic"),
    GEMINI("gemini"),
    JINA_RERANK("jina-rerank"),
    IMAGE_GENERATION("image-generation"),
    EMBEDDINGS("embeddings"),
    OPENAI_VIDEO("openai-video");

    private final String value;

    EndpointTypeEnum(String value) {
        this.value = value;
    }

    /** 从字符串值反查枚举*/
    public static EndpointTypeEnum fromValue(String value) {
        for (EndpointTypeEnum e : values()) {
            if (e.value.equals(value)) return e;
        }
        return null;
    }
}
