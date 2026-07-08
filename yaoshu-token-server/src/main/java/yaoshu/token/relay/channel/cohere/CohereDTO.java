package yaoshu.token.relay.channel.cohere;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Cohere 请求/响应 DTO  */
public final class CohereDTO {
    private CohereDTO() {}

    @Data @Accessors(chain = true) @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CohereRequest {
        private String model;
        @JsonProperty("chat_history")
        private List<ChatHistory> chatHistory;
        private String message;
        private boolean stream;
        @JsonProperty("max_tokens")
        private long maxTokens;
        @JsonProperty("safety_mode")
        private String safetyMode;
    }

    @Data
    public static class ChatHistory {
        private String role;
        private String message;
    }

    @Data
    public static class CohereResponseResult {
        @JsonProperty("response_id")
        private String responseId;
        @JsonProperty("finish_reason")
        private String finishReason;
        private String text;
        private CohereMeta meta;
    }

    @Data
    public static class CohereRerankRequest {
        private List<Object> documents;
        private String query;
        private String model;
        @JsonProperty("top_n")
        private int topN;
        @JsonProperty("return_documents")
        private boolean returnDocuments;
    }

    @Data
    public static class CohereMeta {
        @JsonProperty("billed_units")
        private CohereBilledUnits billedUnits;
    }

    @Data
    public static class CohereBilledUnits {
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outputTokens;
    }
}
