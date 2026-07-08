package yaoshu.token.pojo.dto;

import ai.yue.library.base.convert.Convert;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenAI 通用响应 DTO  * <p>
 * 兼容 OpenAI Chat Completions / Embedding / Responses / Video 等所有响应格式。
 *
 * @author yaoshu
 */
public class OpenAIResponseDTO {

    // ===== 流式响应 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionsStreamResponse {
        private String id;
        private String object;
        private Long created;
        private String model;

        @JsonProperty("system_fingerprint")
        private String systemFingerprint;

        private List<ChatCompletionsStreamResponseChoice> choices;
        private Usage usage;

        public boolean isFinished() {
            return choices != null && !choices.isEmpty()
                    && choices.get(0).getFinishReason() != null
                    && !choices.get(0).getFinishReason().isEmpty();
        }

        public boolean isToolCall() {
            return choices != null && !choices.isEmpty()
                    && choices.get(0).getDelta() != null
                    && choices.get(0).getDelta().getToolCalls() != null
                    && !choices.get(0).getDelta().getToolCalls().isEmpty();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionsStreamResponseChoice {
        private ChatCompletionsStreamResponseChoiceDelta delta;
        private Object logprobs;

        @JsonProperty("finish_reason")
        private String finishReason;

        private Integer index;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionsStreamResponseChoiceDelta {
        private String content;

        @JsonProperty("reasoning_content")
        private String reasoningContent;

        @JsonProperty("reasoning")
        private String reasoning;

        private String role;

        @JsonProperty("tool_calls")
        private List<ToolCallResponse> toolCalls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallResponse {
        private Integer index;
        private String id;

        @JsonProperty("type")
        private Object type;

        private FunctionResponse function;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionResponse {
        private String description;
        private String name;
        private Object parameters;
        private String arguments;
    }

    /** 简化版流式响应（不含 id/model/created） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionsStreamResponseSimple {
        private List<ChatCompletionsStreamResponseChoice> choices;
        private Usage usage;
    }

    // ===== 同步响应 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAITextResponse {
        private String id;
        private String model;
        private String object;
        private Object created;
        private List<OpenAITextResponseChoice> choices;
        private Object error;
        private Usage usage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAITextResponseChoice {
        private Integer index;
        private Message message;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private Object content;

        @JsonProperty("tool_calls")
        private List<ToolCallResponse> toolCalls;

        /**
         * 设置 tool_calls 并清空 content（避免 tool call 场景下两者并存），
         */
        public void setToolCallsAndClearContent(List<ToolCallResponse> tc) {
            this.toolCalls = tc;
            this.content = "";
        }
    }

    // ===== Embedding 响应 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAIEmbeddingResponse {
        private String object;
        private List<OpenAIEmbeddingResponseItem> data;
        private String model;
        private Usage usage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAIEmbeddingResponseItem {
        private String object;
        private Integer index;
        private List<Double> embedding;
    }

    // ===== Responses 响应 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAIResponsesResponse {
        private String id;
        private String object;

        @JsonProperty("created_at")
        private Integer createdAt;

        private Object status;
        private Object error;

        @JsonProperty("incomplete_details")
        private Object incompleteDetails;

        private Object instructions;

        @JsonProperty("max_output_tokens")
        private Integer maxOutputTokens;

        private String model;
        private List<ResponsesOutput> output;

        @JsonProperty("parallel_tool_calls")
        private Boolean parallelToolCalls;

        @JsonProperty("previous_response_id")
        private Object previousResponseId;

        private Object reasoning;
        private Boolean store;
        private Double temperature;

        @JsonProperty("tool_choice")
        private Object toolChoice;

        private List<Object> tools;

        @JsonProperty("top_p")
        private Double topP;

        private Object truncation;
        private Usage usage;
        private Object user;
        private Object metadata;

        /**
         * 从动态错误字段中提取 OpenAIError          */
        public OpenAIError getOpenAIError() {
            if (error == null) return null;
            if (error instanceof OpenAIError e) return e;
            return null;
        }
    }

    // ===== Responses 输出结构 =====

    /**
     * Responses API 输出项      * <p>
     * 支持多种输出类型：message（assistant 文本）、function_call（工具调用）、image_generation_call 等。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponsesOutput {
        private String type;
        private String id;
        private String status;
        private String role;
        private List<ResponsesOutputContent> content;
        private String quality;
        private String size;

        @JsonProperty("call_id")
        private String callId;

        private String name;

        @JsonProperty("arguments")
        private Object arguments;

        /**
         * 返回 function_call arguments 的字符串形式          * <p>
         * JSON 字符串去引号，JSON 对象/数组原样返回，null/空返回 ""。
         */
        public String argumentsString() {
            if (arguments == null) return "";
            if (arguments instanceof String s) {
                String t = s.trim();
                if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
                    return t.substring(1, t.length() - 1);
                }
                return t;
            }
            // JSON 对象/数组 → 需要序列化为字符串
            try {
                return Convert.toJSONString(arguments);
            } catch (Exception e) {
                return arguments.toString();
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponsesOutputContent {
        private String type;
        private String text;
        private List<Object> annotations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponsesReasoningSummaryPart {
        private String type;
        private String text;
    }

    // ===== Responses 流式响应 =====

    /**
     * Responses API 流式事件      * <p>
     * 一个统一的流事件结构，通过 type 字段区分事件类型：
     * response.created / response.output_text.delta / response.output_item.added/done /
     * response.function_call_arguments.delta/done / response.completed / response.error 等。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponsesStreamResponse {
        private String type;
        private OpenAIResponsesResponse response;
        private String delta;
        private ResponsesOutput item;

        @JsonProperty("output_index")
        private Integer outputIndex;

        @JsonProperty("content_index")
        private Integer contentIndex;

        @JsonProperty("summary_index")
        private Integer summaryIndex;

        @JsonProperty("item_id")
        private String itemId;

        private ResponsesReasoningSummaryPart part;
    }

    // ===== 通用 Usage =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        @JsonProperty("prompt_cache_hit_tokens")
        private Integer promptCacheHitTokens;

        @JsonProperty("usage_semantic")
        private String usageSemantic;

        @JsonProperty("usage_source")
        private String usageSource;

        @JsonProperty("prompt_tokens_details")
        private InputTokenDetails promptTokensDetails;

        @JsonProperty("completion_tokens_details")
        private OutputTokenDetails completionTokenDetails;

        @JsonProperty("input_tokens")
        private Integer inputTokens;

        @JsonProperty("output_tokens")
        private Integer outputTokens;

        @JsonProperty("input_tokens_details")
        private InputTokenDetails inputTokensDetails;

        // Claude cache 1h
        @JsonProperty("claude_cache_creation_5_m_tokens")
        private Integer claudeCacheCreation5mTokens;

        @JsonProperty("claude_cache_creation_1_h_tokens")
        private Integer claudeCacheCreation1hTokens;

        // OpenRouter
        private Object cost;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputTokenDetails {
        @JsonProperty("cached_tokens")
        private Integer cachedTokens;

        @JsonProperty("cached_creation_tokens")
        private Integer cachedCreationTokens;

        @JsonProperty("text_tokens")
        private Integer textTokens;

        @JsonProperty("audio_tokens")
        private Integer audioTokens;

        @JsonProperty("image_tokens")
        private Integer imageTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputTokenDetails {
        @JsonProperty("text_tokens")
        private Integer textTokens;

        @JsonProperty("audio_tokens")
        private Integer audioTokens;

        @JsonProperty("image_tokens")
        private Integer imageTokens;

        @JsonProperty("reasoning_tokens")
        private Integer reasoningTokens;
    }

    // ===== Video 响应 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAIVideoResponse {
        private String id;
        private String object;
        private Long bytes;

        @JsonProperty("created_at")
        private Long createdAt;

        @JsonProperty("expires_at")
        private Long expiresAt;

        private String filename;
        private String purpose;
    }
}
