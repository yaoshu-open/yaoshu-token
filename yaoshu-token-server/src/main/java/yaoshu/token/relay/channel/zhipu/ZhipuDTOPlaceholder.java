package yaoshu.token.relay.channel.zhipu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 智谱渠道 DTO 定义  */
public class ZhipuDTOPlaceholder {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ZhipuMessage {
        private String role;
        private String content;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ZhipuRequest {
        private List<ZhipuMessage> prompt;
        private Double temperature;
        @JsonProperty("top_p")
        private Double topP;
        @JsonProperty("request_id")
        private String requestId;
        private Boolean incremental;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZhipuResponseData {
        @JsonProperty("task_id")
        private String taskId;
        @JsonProperty("request_id")
        private String requestId;
        @JsonProperty("task_status")
        private String taskStatus;
        private List<ZhipuMessage> choices;
        private yaoshu.token.pojo.dto.Usage usage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZhipuResponse {
        private int code;
        private String msg;
        private boolean success;
        private ZhipuResponseData data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZhipuStreamMetaResponse {
        @JsonProperty("request_id")
        private String requestId;
        @JsonProperty("task_id")
        private String taskId;
        @JsonProperty("task_status")
        private String taskStatus;
        private yaoshu.token.pojo.dto.Usage usage;
    }
}
