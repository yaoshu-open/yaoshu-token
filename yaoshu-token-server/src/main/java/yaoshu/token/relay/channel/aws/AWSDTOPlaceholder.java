package yaoshu.token.relay.channel.aws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import yaoshu.token.pojo.dto.ClaudeDTO;

import java.util.List;

/**
 * AWS Bedrock 渠道 DTO 定义  * <p>
 * 包含 AWS Claude 请求格式和 Nova（Amazon）请求格式。
 */
public class AWSDTOPlaceholder {

    /**
     * AWS Claude 请求      */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AwsClaudeRequest {
        @JsonProperty("anthropic_version")
        private String anthropicVersion;
        @JsonProperty("anthropic_beta")
        private Object anthropicBeta;
        private Object system;
        private List<ClaudeDTO.ClaudeMessage> messages;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private Double temperature;
        @JsonProperty("top_p")
        private Double topP;
        @JsonProperty("top_k")
        private Integer topK;
        @JsonProperty("stop_sequences")
        private List<String> stopSequences;
        private Object tools;
        @JsonProperty("tool_choice")
        private Object toolChoice;
        private Object thinking;
        @JsonProperty("output_config")
        private Object outputConfig;
    }

    /**
     * Nova 消息内容
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NovaContent {
        private String text;
    }

    /**
     * Nova 消息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NovaMessage {
        private String role;
        private List<NovaContent> content;
    }

    /**
     * Nova 推理配置
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NovaInferenceConfig {
        @JsonProperty("maxTokens")
        private Integer maxTokens;
        @JsonProperty("temperature")
        private Double temperature;
        @JsonProperty("topP")
        private Double topP;
        @JsonProperty("topK")
        private Integer topK;
        @JsonProperty("stopSequences")
        private List<String> stopSequences;
    }

    /**
     * Nova 请求
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NovaRequest {
        @JsonProperty("schemaVersion")
        private String schemaVersion;
        private List<NovaMessage> messages;
        @JsonProperty("inferenceConfig")
        private NovaInferenceConfig inferenceConfig;
    }
}
