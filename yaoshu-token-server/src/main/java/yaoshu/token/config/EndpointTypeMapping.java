package yaoshu.token.config;

import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.EndpointTypeEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * 渠道类型 → 端点类型映射  */
public final class EndpointTypeMapping {

    private EndpointTypeMapping() {
    }

    /**
     * 根据渠道类型获取推荐的端点类型列表（按优先级排序）
     * <p>
     * 所有渠道都支持 OpenAI 端点作为兜底。
     *
     * @param channelType 渠道类型
     * @param modelName   模型名（用于判断特殊模型）
     * @return 端点类型列表，第一个为最优先
     */
    public static List<EndpointTypeEnum> getEndpointTypesByChannelType(int channelType, String modelName) {
        List<EndpointTypeEnum> endpointTypes;

        switch (channelType) {
            case ChannelConstants.CHANNEL_TYPE_JINA:
                endpointTypes = List.of(EndpointTypeEnum.JINA_RERANK);
                break;
            case ChannelConstants.CHANNEL_TYPE_AWS:
            case ChannelConstants.CHANNEL_TYPE_ANTHROPIC:
                endpointTypes = List.of(EndpointTypeEnum.ANTHROPIC, EndpointTypeEnum.OPENAI);
                break;
            case ChannelConstants.CHANNEL_TYPE_VERTEX_AI:
            case ChannelConstants.CHANNEL_TYPE_GEMINI:
                endpointTypes = List.of(EndpointTypeEnum.GEMINI, EndpointTypeEnum.OPENAI);
                break;
            case ChannelConstants.CHANNEL_TYPE_OPEN_ROUTER:
                // OpenRouter 只支持 OpenAI 端点
                endpointTypes = List.of(EndpointTypeEnum.OPENAI);
                break;
            case ChannelConstants.CHANNEL_TYPE_XAI:
                endpointTypes = List.of(EndpointTypeEnum.OPENAI, EndpointTypeEnum.OPENAI_RESPONSE);
                break;
            case ChannelConstants.CHANNEL_TYPE_SORA:
                endpointTypes = List.of(EndpointTypeEnum.OPENAI_VIDEO);
                break;
            default:
                if (ModelMappingConfig.isOpenAIResponseOnlyModel(modelName)) {
                    endpointTypes = List.of(EndpointTypeEnum.OPENAI_RESPONSE);
                } else {
                    endpointTypes = List.of(EndpointTypeEnum.OPENAI);
                }
                break;
        }

        // 图像生成模型 → 把图像生成端点放到最前面
        if (ModelMappingConfig.isImageGenerationModel(modelName)) {
            List<EndpointTypeEnum> result = new ArrayList<>();
            result.add(EndpointTypeEnum.IMAGE_GENERATION);
            result.addAll(endpointTypes);
            return result;
        }

        return endpointTypes;
    }
}
