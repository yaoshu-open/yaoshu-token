package yaoshu.token.relay.channel.tencent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 腾讯混元渠道 DTO 定义  * <p>
 * 腾讯混元 API 使用 PascalCase 命名风格。
 */
public class TencentDTOPlaceholder {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TencentMessage {
        private String Role;
        private String Content;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TencentChatRequest {
        private String Model;
        private List<TencentMessage> Messages;
        private Boolean Stream;
        private Double TopP;
        private Double Temperature;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TencentError {
        private int Code;
        private String Message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TencentUsage {
        @JsonProperty("PromptTokens")
        private int promptTokens;
        @JsonProperty("CompletionTokens")
        private int completionTokens;
        @JsonProperty("TotalTokens")
        private int totalTokens;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TencentResponseChoices {
        @JsonProperty("FinishReason")
        private String finishReason;
        @JsonProperty("Message")
        private TencentMessage message;
        @JsonProperty("Delta")
        private TencentMessage delta;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TencentChatResponse {
        private List<TencentResponseChoices> Choices;
        private long Created;
        private String Id;
        private TencentUsage Usage;
        private TencentError Error;
        private String Note;
        @JsonProperty("Req_id")
        private String reqId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TencentChatResponseSB {
        private TencentChatResponse Response;
    }
}
