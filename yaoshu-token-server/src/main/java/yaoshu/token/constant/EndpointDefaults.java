package yaoshu.token.constant;

import jakarta.annotation.Nullable;

import java.util.Map;

/**
 * 内置端点类型的默认 Path 与 Method  */
public final class EndpointDefaults {

    private EndpointDefaults() {
    }

    /** 端点默认信息 */
    public record EndpointInfo(String path, String method) {
    }

    /** 内置端点的默认 Path 与 Method 映射 */
    private static final Map<EndpointTypeEnum, EndpointInfo> DEFAULT_ENDPOINT_MAP = Map.of(
            EndpointTypeEnum.OPENAI,                 new EndpointInfo("/v1/chat/completions", "POST"),
            EndpointTypeEnum.OPENAI_RESPONSE,         new EndpointInfo("/v1/responses", "POST"),
            EndpointTypeEnum.OPENAI_RESPONSE_COMPACT, new EndpointInfo("/v1/responses/compact", "POST"),
            EndpointTypeEnum.ANTHROPIC,               new EndpointInfo("/v1/messages", "POST"),
            EndpointTypeEnum.GEMINI,                  new EndpointInfo("/v1beta/models/{model}:generateContent", "POST"),
            EndpointTypeEnum.JINA_RERANK,             new EndpointInfo("/v1/rerank", "POST"),
            EndpointTypeEnum.IMAGE_GENERATION,        new EndpointInfo("/v1/images/generations", "POST"),
            EndpointTypeEnum.EMBEDDINGS,              new EndpointInfo("/v1/embeddings", "POST")
    );

    /**
     * 返回指定端点类型的默认信息
     *
     * @param endpointType 端点类型
     * @return 默认信息；若类型不在内置表中，返回 null
     */
    @Nullable
    public static EndpointInfo getDefaultEndpointInfo(EndpointTypeEnum endpointType) {
        return DEFAULT_ENDPOINT_MAP.get(endpointType);
    }
}
