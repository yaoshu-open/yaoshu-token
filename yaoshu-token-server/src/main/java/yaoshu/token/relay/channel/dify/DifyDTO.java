package yaoshu.token.relay.channel.dify;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * Dify 请求/响应 DTO  */
public final class DifyDTO {
    private DifyDTO() {}

    @Data @Accessors(chain = true) @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DifyChatRequest {
        private Map<String, Object> inputs;
        private String query;
        @JsonProperty("response_mode")
        private String responseMode;
        private String user;
        @JsonProperty("auto_generate_name")
        private boolean autoGenerateName;
        private List<DifyFile> files;
    }

    @Data
    public static class DifyFile {
        private String type;
        @JsonProperty("transfer_mode")
        private String transferMode;
        private String url;
        @JsonProperty("upload_file_id")
        private String uploadFileId;
    }

    @Data
    public static class DifyChatCompletionResponse {
        @JsonProperty("conversation_id")
        private String conversationId;
        private String answer;
        @JsonProperty("create_at")
        private long createAt;
        private Map<String, Object> metadata;
    }

    @Data
    public static class DifyChunkChatCompletionResponse {
        private String event;
        @JsonProperty("conversation_id")
        private String conversationId;
        private String answer;
        private Object data;
        private Map<String, Object> metadata;
    }
}
