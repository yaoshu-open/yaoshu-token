package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI 兼容服务  * <p>
 * 处理 OpenAI 兼容性格式检测与转换（模型名正则匹配等）。
 */
@Slf4j
public class OpenAICompatService {

    /**
     * 检查模型名是否匹配 OpenAI 兼容格式
     *
     * @param modelName 模型名
     * @return 是否兼容
     */
    public boolean isOpenAICompatible(String modelName) {
        if (modelName == null || modelName.isEmpty()) return false;
        // OpenAI 标准模型名格式检测
        return modelName.startsWith("gpt-") || modelName.startsWith("o1") || modelName.startsWith("o3")
                || modelName.startsWith("o4") || modelName.startsWith("chatgpt-");
    }

    /**
     * 检查是否为 Claude 格式兼容请求
     */
    public boolean isClaudeCompatible(String path) {
        return path != null && path.contains("/v1/messages");
    }
}
