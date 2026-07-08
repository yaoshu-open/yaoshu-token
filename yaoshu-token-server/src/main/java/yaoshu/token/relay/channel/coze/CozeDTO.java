package yaoshu.token.relay.channel.coze;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Coze 请求/响应 DTO  */
public final class CozeDTO {
    private CozeDTO() {}
    @Data @Accessors(chain = true) @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CozeChatRequest {
        @JsonProperty("bot_id") private String botId;
        @JsonProperty("user_id") private String userId;
        @JsonProperty("additional_messages") private List<CozeMessage> additionalMessages;
        private boolean stream;
        @JsonProperty("auto_save_history") private boolean autoSaveHistory = true;
    }
    @Data
    public static class CozeMessage {
        private String role;
        private String content;
        @JsonProperty("content_type") private String contentType = "text";
    }
    @Data
    public static class CozeChatResponse {
        private int code;
        private String msg;
        private CozeData data;
    }
    @Data
    public static class CozeData {
        private String id;
        @JsonProperty("conversation_id") private String conversationId;
        private String status;
    }
}
