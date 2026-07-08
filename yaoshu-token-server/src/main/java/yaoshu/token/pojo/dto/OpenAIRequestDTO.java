package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 通用请求 DTO  * <p>
 * 兼容 OpenAI Chat Completions API 的通用请求结构。
 *
 * @author yaoshu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAIRequestDTO {

    private String model;
    private List<Message> messages;
    private Object prompt;
    private Object prefix;
    private Object suffix;
    private Boolean stream;

    @JsonProperty("stream_options")
    private StreamOptions streamOptions;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;

    @JsonProperty("reasoning_effort")
    private String reasoningEffort;

    @JsonProperty("verbosity")
    private Object verbosity;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("top_k")
    private Integer topK;

    private Object stop;

    @JsonProperty("n")
    private Integer n;

    private Object input;
    private String instruction;
    private String size;
    private Object functions;

    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    @JsonProperty("response_format")
    private ResponseFormat responseFormat;

    @JsonProperty("encoding_format")
    private Object encodingFormat;

    private Double seed;

    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    private List<ToolCallRequest> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    @JsonProperty("function_call")
    private Object functionCall;

    private Object user;

    @JsonProperty("service_tier")
    private Object serviceTier;

    private Boolean logprobs;

    @JsonProperty("top_logprobs")
    private Integer topLogprobs;

    private Integer dimensions;
    private Object modalities;
    private Object audio;

    @JsonProperty("safety_identifier")
    private Object safetyIdentifier;

    private Object store;

    @JsonProperty("prompt_cache_key")
    private String promptCacheKey;

    @JsonProperty("prompt_cache_retention")
    private Object promptCacheRetention;

    @JsonProperty("logit_bias")
    private Object logitBias;

    private Object metadata;
    private Object prediction;

    @JsonProperty("extra_body")
    private Object extraBody;

    @JsonProperty("search_parameters")
    private Object searchParameters;

    @JsonProperty("web_search_options")
    private Object webSearchOptions;

    // OpenRouter
    private Object usage;
    private Object reasoning;

    // Ali Qwen
    @JsonProperty("vl_high_resolution_images")
    private Object vlHighResolutionImages;

    @JsonProperty("enable_thinking")
    private Object enableThinking;

    @JsonProperty("chat_template_kwargs")
    private Object chatTemplateKwargs;

    @JsonProperty("enable_search")
    private Object enableSearch;

    // ollama
    private Object think;

    // baidu v2
    @JsonProperty("web_search")
    private Object webSearch;

    // doubao, zhipu_v4
    @JsonProperty("thinking")
    private Object thinking;

    // pplx
    @JsonProperty("search_domain_filter")
    private Object searchDomainFilter;

    @JsonProperty("search_recency_filter")
    private Object searchRecencyFilter;

    @JsonProperty("return_images")
    private Boolean returnImages;

    @JsonProperty("return_related_questions")
    private Boolean returnRelatedQuestions;

    @JsonProperty("search_mode")
    private Object searchMode;

    // Minimax
    @JsonProperty("reasoning_split")
    private Object reasoningSplit;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private Object content;
        private String name;
        private Boolean prefix;

        @JsonProperty("reasoning_content")
        private String reasoningContent;

        @JsonProperty("reasoning")
        private String reasoning;

        @JsonProperty("tool_calls")
        private Object toolCalls;

        @JsonProperty("tool_call_id")
        private String toolCallId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallRequest {
        private String id;
        private String type;
        private FunctionRequest function;
        private Object custom;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionRequest {
        private String description;
        private String name;
        private Object parameters;
        private String arguments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamOptions {
        @JsonProperty("include_usage")
        private Boolean includeUsage;

        @JsonProperty("include_obfuscation")
        private Boolean includeObfuscation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseFormat {
        private String type;

        @JsonProperty("json_schema")
        private Object jsonSchema;
    }
}
