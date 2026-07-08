package yaoshu.token.relay.channel.task.ali;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

public class TaskAliDTO {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliVideoRequest {
        private String model;
        private AliVideoInput input;
        private AliVideoParameters parameters;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliVideoInput {
        private String prompt;
        @JsonProperty("img_url")
        private String imgUrl;
        @JsonProperty("first_frame_url")
        private String firstFrameUrl;
        @JsonProperty("last_frame_url")
        private String lastFrameUrl;
        @JsonProperty("audio_url")
        private String audioUrl;
        @JsonProperty("negative_prompt")
        private String negativePrompt;
        private String template;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliVideoParameters {
        private String resolution;
        private String size;
        private Integer duration;
        @JsonProperty("prompt_extend")
        private Boolean promptExtend;
        private Boolean watermark;
        private Boolean audio;
        private Integer seed;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliVideoResponse {
        private AliVideoOutput output;
        @JsonProperty("request_id")
        private String requestId;
        private String code;
        private String message;
        private AliUsage usage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliVideoOutput {
        @JsonProperty("task_id")
        private String taskId;
        @JsonProperty("task_status")
        private String taskStatus;
        @JsonProperty("submit_time")
        private String submitTime;
        @JsonProperty("scheduled_time")
        private String scheduledTime;
        @JsonProperty("end_time")
        private String endTime;
        @JsonProperty("orig_prompt")
        private String origPrompt;
        @JsonProperty("actual_prompt")
        private String actualPrompt;
        @JsonProperty("video_url")
        private String videoUrl;
        private String code;
        private String message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliUsage {
        private Integer duration;
        @JsonProperty("video_count")
        private Integer videoCount;
        @JsonProperty("SR")
        private Integer sr;
    }
}
