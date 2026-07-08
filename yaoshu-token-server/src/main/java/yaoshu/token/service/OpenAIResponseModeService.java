package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI Responses 模式服务  * <p>
 * 处理 Responses API 的模式判断（compact/chat 转换等）。
 */
@Slf4j
public class OpenAIResponseModeService {

    /**
     * 检查是否为 Responses 模式请求
     */
    public boolean isResponsesMode(String path) {
        return path != null && path.contains("/v1/responses");
    }

    /**
     * 检查是否需要 compact 转换
     */
    public boolean isCompactMode(String path) {
        return path != null && path.contains("/compact");
    }
}
