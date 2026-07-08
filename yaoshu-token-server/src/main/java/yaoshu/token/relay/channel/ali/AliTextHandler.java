package yaoshu.token.relay.channel.ali;

import yaoshu.token.pojo.dto.GeneralOpenAIRequest;

/**
 * 阿里 Chat Completions 中转处理器  * <p>
 * 阿里通义千问兼容 OpenAI 协议，仅需对 topP 参数做边界限制。
 */
public class AliTextHandler {

    /**
     * OpenAI 请求 → 阿里请求转换      * <p>
     * 阿里要求 topP 在 (0, 1) 开区间内，超界时做钳位。
     */
    public static GeneralOpenAIRequest requestOpenAI2Ali(GeneralOpenAIRequest request) {
        Double topP = request.getTopP();
        if (topP != null) {
            if (topP >= 1) {
                request.setTopP(0.999);
            } else if (topP <= 0) {
                request.setTopP(0.001);
            }
        } else {
            // topP 为 null 时设默认值，确保阿里不报错
            request.setTopP(0.001);
        }
        return request;
    }
}
