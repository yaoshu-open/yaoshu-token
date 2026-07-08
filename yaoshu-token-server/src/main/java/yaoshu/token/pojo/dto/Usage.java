package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的 Usage 对象，对应 OpenAI API 的 usage 字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Usage {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;

    /** 提示缓存命中 tokens（Claude/OpenRouter 场景） */
    private int promptCacheHitTokens;
    /** usage 语义类型："anthropic" / "openai"，决定 token 归一化策略 */
    private String usageSemantic;
    /** usage 来源标识 */
    private String usageSource;
    /** 输入 tokens 明细（OpenAI responses API） */
    private InputTokensDetails inputTokensDetails;

    /** Claude 格式的输入/输出 tokens（Claude API 原生字段） */
    private int inputTokens;
    private int outputTokens;

    /** Claude 缓存创建 tokens — 5分钟 TTL */
    private int claudeCacheCreation5mTokens;
    /** Claude 缓存创建 tokens — 1小时 TTL */
    private int claudeCacheCreation1hTokens;

    /** OpenRouter 费用（原始值透传） */
    private Object cost;

    @Builder.Default
    private PromptTokensDetails promptTokensDetails = new PromptTokensDetails();

    @Builder.Default
    private CompletionTokenDetails completionTokenDetails = new CompletionTokenDetails();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptTokensDetails {
        private int cachedTokens;
        /** 缓存创建 tokens（GPT 格式的 cached_creation_tokens） */
        private int cachedCreationTokens;
        private int audioTokens;
        private int textTokens;
        private int imageTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletionTokenDetails {
        private int reasoningTokens;
        private int audioTokens;
        private int textTokens;
        /** 图片输出 tokens */
        private int imageTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputTokensDetails {
        private int textTokens;
        private int audioTokens;
        private int imageTokens;
        private int cachedTokens;
    }
}
