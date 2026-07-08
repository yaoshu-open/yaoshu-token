package yaoshu.token.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 通用请求体  */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralOpenAIRequest {
    private String model;
    private List<Message> messages;
    /** Completions prompt，可为字符串或数组 */
    private Object prompt;
    /** Completions suffix */
    private Object suffix;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Integer maxTokens;
    private Integer maxCompletionTokens;
    private Double seed;
    private Boolean stream;
    private String user;
    private Double frequencyPenalty;
    private Double presencePenalty;
    /** stop 可以是 String 或 String[] */
    private Object stop;
    private Integer n;
    private Object responseFormat;
    /** Gemini / OpenAI 扩展透传参数，如 extra_body.google */
    private Object extraBody;
    /** logit_bias */
    private Map<String, Integer> logitBias;
    private Boolean logProbs;
    private Integer topLogProbs;
    private Object streamOptions;
    /** 推理力度（OpenAI o1/o3 系列） */
    private String reasoningEffort;
    /** Anthropic thinking 转 content 标志 */
    private Boolean thinkingToContent;
    /** Claude Beta 查询标志 */
    private Boolean claudeBetaQuery;
    /** 工具定义 */
    private List<Object> tools;
    /** 工具选择 */
    private Object toolChoice;
    /** 并行工具调用 */
    private Boolean parallelToolCalls;
    /** 函数调用（废弃） */
    private Object functions;
    private Object functionCall;
    /** 预测输出（OpenAI SSE） */
    private Object prediction;
    /** store */
    private Boolean store;
    /** metadata */
    private Map<String, String> metadata;
    /** 服务层级 */
    private String serviceTier;
    /** Ollama think 透传字段 */
    private Object think;

    /** usage 控制（OpenAI stream_options.include_usage 等价） */
    private Map<String, Boolean> usage;

    /** Web 搜索选项*/
    @JsonProperty("web_search_options")
    private WebSearchOptions webSearchOptions;

    // 便捷方法（兼容 boolean 和 Boolean）
    public boolean isStream() { return Boolean.TRUE.equals(stream); }
    public boolean isThinkingToContent() { return Boolean.TRUE.equals(thinkingToContent); }
    public boolean isClaudeBetaQuery() { return Boolean.TRUE.equals(claudeBetaQuery); }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WebSearchOptions {
        /** 搜索上下文大小：low / medium / high */
        @JsonProperty("search_context_size")
        private String searchContextSize;
        /** 用户位置信息 */
        private Object userLocation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        private String role;
        private Object content;
        private String name;
        private List<Object> toolCalls;
        private String toolCallId;
    }
}
