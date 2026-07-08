package yaoshu.token.relay.channel.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * Ollama 请求/响应 DTO  */
public final class OllamaDTO {

    private OllamaDTO() {
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OllamaChatMessage {
        private String role;
        private String content;
        private List<String> images;
        @JsonProperty("tool_calls")
        private List<OllamaToolCall> toolCalls;
        @JsonProperty("tool_name")
        private String toolName;
        private Object thinking;
    }

    @Data
    public static class OllamaToolFunction {
        private String name;
        private String description;
        private Object parameters;
    }

    @Data
    public static class OllamaTool {
        private String type;
        private OllamaToolFunction function;
    }

    @Data
    public static class OllamaToolCall {
        private FunctionPart function;

        @Data
        public static class FunctionPart {
            private String name;
            private Object arguments;
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OllamaChatRequest {
        private String model;
        private List<OllamaChatMessage> messages;
        private Object tools;
        private Object format;
        private Boolean stream;
        private Map<String, Object> options;
        @JsonProperty("keep_alive")
        private Object keepAlive;
        private Object think;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OllamaGenerateRequest {
        private String model;
        private String prompt;
        private String suffix;
        private List<String> images;
        private Object format;
        private Boolean stream;
        private Map<String, Object> options;
        @JsonProperty("keep_alive")
        private Object keepAlive;
        private Object think;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OllamaEmbeddingRequest {
        private String model;
        private Object input;
        private Map<String, Object> options;
        private int dimensions;
    }

    @Data
    public static class OllamaEmbeddingResponse {
        private String error;
        private String model;
        private List<List<Double>> embeddings;
        @JsonProperty("prompt_eval_count")
        private int promptEvalCount;
    }
}
