package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Claude API 请求/响应 DTO  *
 * @author yaoshu
 */
public class ClaudeDTO {

    // ===== 请求 =====

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeRequest {
        private String model;
        private String prompt;
        private Object system;
        private List<ClaudeMessage> messages;

        @JsonProperty("cache_control")
        private Object cacheControl;

        @JsonProperty("inference_geo")
        private String inferenceGeo;

        @JsonProperty("max_tokens")
        private Integer maxTokens;

        @JsonProperty("max_tokens_to_sample")
        private Integer maxTokensToSample;

        @JsonProperty("stop_sequences")
        private List<String> stopSequences;

        private Double temperature;

        @JsonProperty("top_p")
        private Double topP;

        @JsonProperty("top_k")
        private Integer topK;

        private Boolean stream;
        private Object tools;

        @JsonProperty("context_management")
        private Object contextManagement;

        @JsonProperty("output_config")
        private Object outputConfig;

        @JsonProperty("output_format")
        private Object outputFormat;

        private Object container;

        @JsonProperty("tool_choice")
        private Object toolChoice;

        private Thinking thinking;

        @JsonProperty("mcp_servers")
        private Object mcpServers;

        private Object metadata;

        @JsonProperty("speed")
        private Object speed;

        @JsonProperty("service_tier")
        private String serviceTier;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeMessage {
        private String role;
        private Object content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeMediaMessage {
        private String type;
        private String text;
        private String model;
        private ClaudeMessageSource source;
        private ClaudeUsage usage;

        @JsonProperty("stop_reason")
        private String stopReason;

        @JsonProperty("partial_json")
        private String partialJson;

        private String role;
        private String thinking;
        private String signature;
        private String delta;

        @JsonProperty("cache_control")
        private Object cacheControl;

        private String id;
        private String name;
        private Object input;
        private Object content;

        @JsonProperty("tool_use_id")
        private String toolUseId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeMessageSource {
        private String type;

        @JsonProperty("media_type")
        private String mediaType;

        private Object data;
        private String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Thinking {
        private String type;

        @JsonProperty("budget_tokens")
        private Integer budgetTokens;

        private String display;
    }

    // ===== 响应 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeResponse {
        private String id;
        private String type;
        private String role;
        private List<ClaudeMediaMessage> content;
        private String completion;

        @JsonProperty("stop_reason")
        private String stopReason;

        private String model;
        private Object error;
        private ClaudeUsage usage;
        private Integer index;

        @JsonProperty("content_block")
        private ClaudeMediaMessage contentBlock;

        private ClaudeMediaMessage delta;
        private ClaudeMediaMessage message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeUsage {
        @JsonProperty("input_tokens")
        private Integer inputTokens;

        @JsonProperty("cache_creation_input_tokens")
        private Integer cacheCreationInputTokens;

        @JsonProperty("cache_read_input_tokens")
        private Integer cacheReadInputTokens;

        @JsonProperty("output_tokens")
        private Integer outputTokens;

        @JsonProperty("cache_creation")
        private ClaudeCacheCreationUsage cacheCreation;

        @JsonProperty("claude_cache_creation_5_m_tokens")
        private Integer claudeCacheCreation5mTokens;

        @JsonProperty("claude_cache_creation_1_h_tokens")
        private Integer claudeCacheCreation1hTokens;

        @JsonProperty("server_tool_use")
        private ClaudeServerToolUse serverToolUse;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeCacheCreationUsage {
        @JsonProperty("ephemeral_5m_input_tokens")
        private Integer ephemeral5mInputTokens;

        @JsonProperty("ephemeral_1h_input_tokens")
        private Integer ephemeral1hInputTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaudeServerToolUse {
        @JsonProperty("web_search_requests")
        private Integer webSearchRequests;
    }
}
