package yaoshu.token.relay.channel.zhipu4v;

import yaoshu.token.pojo.dto.GeneralOpenAIRequest;

/**
 * 智谱 GLM-4V 中转处理器  * <p>
 * GLM-4V 兼容 OpenAI 协议，但需对图片 base64 前缀做清理。
 */
public class Zhipu4VRelayHandler {

    /**
     * OpenAI 请求 → 智谱 4V 请求      * <p>
     * GLM-4V 兼容 OpenAI 格式，直接透传（图片 base64 清理由 Convert 层处理）。
     */
    public static GeneralOpenAIRequest requestOpenAI2Zhipu4V(GeneralOpenAIRequest request) {
        return request;
    }
}
