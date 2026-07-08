package yaoshu.token.constant;

import lombok.Getter;

/**
 * API 类型枚举  */
@Getter
public enum ApiTypeEnum {

    OPENAI(0),
    ANTHROPIC(1),
    PALM(2),
    BAIDU(3),
    ZHIPU(4),
    ALI(5),
    XUNFEI(6),
    AI_PROXY_LIBRARY(7),
    TENCENT(8),
    GEMINI(9),
    ZHIPU_V4(10),
    OLLAMA(11),
    PERPLEXITY(12),
    AWS(13),
    COHERE(14),
    DIFY(15),
    JINA(16),
    CLOUDFLARE(17),
    SILICON_FLOW(18),
    VERTEX_AI(19),
    MISTRAL(20),
    DEEP_SEEK(21),
    MOKA_AI(22),
    VOLC_ENGINE(23),
    BAIDU_V2(24),
    OPEN_ROUTER(25),
    XINFERENCE(26),
    XAI(27),
    COZE(28),
    JIMENG(29),
    MOONSHOT(30),
    SUBMODEL(31),
    MINI_MAX(32),
    REPLICATE(33),
    CODEX(34),
    /** 仅用于计数，不可在此之后添加新渠道 */
    DUMMY(35);

    private final int code;

    ApiTypeEnum(int code) {
        this.code = code;
    }
}
