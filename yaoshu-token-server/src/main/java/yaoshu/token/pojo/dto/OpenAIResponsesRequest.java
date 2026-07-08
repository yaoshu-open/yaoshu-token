package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Responses API 请求体  * <p>
 * https://platform.openai.com/docs/api-reference/responses/create
 *
 * @author yaoshu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAIResponsesRequest {

    private String model;
    private Object input;
    private Object include;
    private Object conversation;

    @JsonProperty("context_management")
    private Object contextManagement;

    private Object instructions;

    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;

    @JsonProperty("top_logprobs")
    private Integer topLogProbs;

    private Object metadata;

    @JsonProperty("parallel_tool_calls")
    private Object parallelToolCalls;

    @JsonProperty("previous_response_id")
    private String previousResponseId;

    private Reasoning reasoning;

    /** 上游服务层级，可能影响计费。默认被过滤，可通过渠道设置 allow_service_tier 开启 */
    @JsonProperty("service_tier")
    private String serviceTier;

    /** 上游是否可存储请求/响应数据。默认允许，可通过渠道设置 disable_store 禁用 */
    private Object store;

    @JsonProperty("prompt_cache_key")
    private Object promptCacheKey;

    @JsonProperty("prompt_cache_retention")
    private Object promptCacheRetention;

    /** 客户端标识，用于策略滥用检测。默认被过滤，可通过渠道设置 allow_safety_identifier 开启 */
    @JsonProperty("safety_identifier")
    private Object safetyIdentifier;

    private Boolean stream;

    @JsonProperty("stream_options")
    private OpenAIRequestDTO.StreamOptions streamOptions;

    private Double temperature;
    private Object text;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    /** tools —— MCP 参数太多不确定，用 Object 承载 */
    private Object tools;

    @JsonProperty("top_p")
    private Double topP;

    private Object truncation;
    private Object user;

    @JsonProperty("max_tool_calls")
    private Integer maxToolCalls;

    private Object prompt;

    /** Qwen 特有参数 */
    @JsonProperty("enable_thinking")
    private Object enableThinking;

    /** Perplexity 特有参数 */
    private Object preset;

    // ======================== 内部类型 ========================

    /**
     * 推理配置      */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reasoning {
        private String effort;
        private String summary;
    }

    /**
     * Responses API input 数组中的原始元素      */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Input {
        private String type;
        private String role;
        private Object content;
    }

    /**
     * 归一化后的多媒体输入元素      * <p>
     * 由 ParseInput() 将 Input 数组中的 image/file/text 项归一化为此结构。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaInput {
        private String type;

        @JsonProperty("text")
        private String text;

        @JsonProperty("file_url")
        private String fileUrl;

        @JsonProperty("image_url")
        private String imageUrl;

        /** 仅 input_image 有效 */
        @JsonProperty("detail")
        private String detail;
    }
}
