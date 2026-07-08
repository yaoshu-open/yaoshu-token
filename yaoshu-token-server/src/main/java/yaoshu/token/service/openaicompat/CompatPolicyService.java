package yaoshu.token.service.openaicompat;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI 兼容策略服务  * <p>
 * 根据模型名/渠道类型决定兼容性策略（如是否禁用 stream options）。
 */
@Slf4j
public class CompatPolicyService {

    /**
     * 检查模型是否支持 stream_options      */
    public boolean supportsStreamOptions(String modelName) {
        if (modelName == null) return false;
        // 大部分 OpenAI 兼容模型支持 stream_options
        return CompatRegexUtils.isOpenAICompatibleModel(modelName);
    }

    /**
     * 检查模型是否需要特殊的参数处理
     */
    public boolean needsParamCleanup(String modelName) {
        if (modelName == null) return false;
        // O 系列模型需要清理 temperature/top_p 等参数
        return CompatRegexUtils.isOSeriesModel(modelName);
    }
}
