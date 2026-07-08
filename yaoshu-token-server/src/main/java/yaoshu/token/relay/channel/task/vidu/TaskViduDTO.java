package yaoshu.token.relay.channel.task.vidu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

public class TaskViduDTO {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestPayload {
        private String model;
        private List<String> images;
        private String prompt;
        private Integer duration;
        private Integer seed;
        private String resolution;
        @JsonProperty("movement_amplitude")
        private String movementAmplitude;
        private Boolean bgm;
        private String payload;
        @JsonProperty("callback_url")
        private String callbackUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponsePayload {
        @JsonProperty("task_id")
        private String taskId;
        private String state;
        private String model;
        private List<String> images;
        private String prompt;
        private Integer duration;
        private Integer seed;
        private String resolution;
        private Boolean bgm;
        @JsonProperty("movement_amplitude")
        private String movementAmplitude;
        private String payload;
        @JsonProperty("created_at")
        private String createdAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskResultResponse {
        private String state;
        @JsonProperty("err_code")
        private String errCode;
        private Integer credits;
        private String payload;
        private List<Creation> creations;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Creation {
        private String id;
        private String url;
        @JsonProperty("cover_url")
        private String coverUrl;
    }
}
