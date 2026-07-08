package yaoshu.token.config.model;

import lombok.Data;

import java.util.List;

/**
 * 全局模型设置 POJO  */
@Data
public class GlobalModelSettingConfig {

    /** 全局单例*/
    private static final GlobalModelSettingConfig INSTANCE = new GlobalModelSettingConfig();

    /** 获取全局单例*/
    public static GlobalModelSettingConfig getInstance() { return INSTANCE; }

    /** 是否启用全局 PassThrough 请求透传 */
    private boolean passThroughRequestEnabled;

    /** 思维模型黑名单（保留 thinking 后缀的模型） */
    private List<String> thinkingModelBlacklist = List.of(
            "moonshotai/kimi-k2-thinking", "kimi-k2-thinking");

    /** Chat Completions 转 Responses 策略 */
    private ChatCompletionsToResponsesPolicy chatCompletionsToResponsesPolicy = new ChatCompletionsToResponsesPolicy();

    /**
     * 检查模型是否在思维黑名单中（应保留 thinking 后缀）      */
    public boolean shouldPreserveThinkingSuffix(String modelName) {
        if (modelName == null || modelName.isEmpty()) return false;
        return thinkingModelBlacklist.contains(modelName.trim());
    }

    /**
     * Chat Completions → Responses 转换策略
     */
    @Data
    public static class ChatCompletionsToResponsesPolicy {
        private boolean enabled;
        private boolean allChannels = true;
        private List<Integer> channelIds;
        private List<Integer> channelTypes;
        private List<String> modelPatterns;

        /**
         * 判断指定渠道是否启用          */
        public boolean isChannelEnabled(int channelId, int channelType) {
            if (!enabled) return false;
            if (allChannels) return true;
            if (channelId > 0 && channelIds != null && channelIds.contains(channelId)) return true;
            if (channelType > 0 && channelTypes != null && channelTypes.contains(channelType)) return true;
            return false;
        }
    }
}
