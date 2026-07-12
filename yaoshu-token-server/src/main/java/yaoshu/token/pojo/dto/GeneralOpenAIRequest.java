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
    @JsonProperty("top_p")
    private Double topP;
    @JsonProperty("top_k")
    private Integer topK;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;
    private Double seed;
    private Boolean stream;
    private String user;
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;
    @JsonProperty("presence_penalty")
    private Double presencePenalty;
    /** stop 可以是 String 或 String[] */
    private Object stop;
    private Integer n;
    @JsonProperty("response_format")
    private Object responseFormat;
    /** Gemini / OpenAI 扩展透传参数，如 extra_body.google */
    @JsonProperty("extra_body")
    private Object extraBody;
    /** logit_bias */
    @JsonProperty("logit_bias")
    private Map<String, Integer> logitBias;
    @JsonProperty("logprobs")
    private Boolean logProbs;
    @JsonProperty("top_logprobs")
    private Integer topLogProbs;
    @JsonProperty("stream_options")
    private Object streamOptions;
    /** 推理力度（OpenAI o1/o3 系列） */
    @JsonProperty("reasoning_effort")
    private String reasoningEffort;
    /** Anthropic thinking 转 content 标志 */
    @JsonProperty("thinking_to_content")
    private Boolean thinkingToContent;
    /** Claude Beta 查询标志 */
    @JsonProperty("claude_beta_query")
    private Boolean claudeBetaQuery;
    /** 工具定义 */
    private List<Object> tools;
    /** 工具选择 */
    @JsonProperty("tool_choice")
    private Object toolChoice;
    /** 并行工具调用 */
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;
    /** 函数调用（废弃） */
    private Object functions;
    @JsonProperty("function_call")
    private Object functionCall;
    /** 预测输出（OpenAI SSE） */
    private Object prediction;
    /** store */
    private Boolean store;
    /** metadata */
    private Map<String, String> metadata;
    /** 服务层级 */
    @JsonProperty("service_tier")
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
        @JsonProperty("tool_calls")
        private List<Object> toolCalls;
        @JsonProperty("tool_call_id")
        private String toolCallId;
        /** DeepSeek 等思考模式的推理内容，多轮对话时需回传给上游 API */
        @JsonProperty("reasoning_content")
        private String reasoningContent;
    }
}
