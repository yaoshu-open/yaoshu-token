package yaoshu.token.relay.channel.xai;

import yaoshu.token.pojo.dto.GeneralOpenAIRequest;

/**
 * XAI（Grok）Chat Completions 中转处理器  * <p>
 * xAI 使用 OpenAI 兼容协议，无需特殊请求转换。
 */
public class XAITextHandler {

    /**
     * OpenAI 请求 → XAI 请求（透传）
     * <p>
     * xAI 完全兼容 OpenAI 格式，仅去除不支持的参数。
     */
    public static GeneralOpenAIRequest requestOpenAI2XAI(GeneralOpenAIRequest request) {
        // xAI 不需要特殊转换，直接透传
        return request;
    }
}
