package yaoshu.token.relay.channel.task.doubao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

public class TaskDoubaoDTO {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentItem {
        private String type;
        private String text;
        @JsonProperty("image_url")
        private MediaURL imageUrl;
        @JsonProperty("video_url")
        private MediaURL videoUrl;
        @JsonProperty("audio_url")
        private MediaURL audioUrl;
        private String role;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MediaURL {
        private String url;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestPayload {
        private String model;
        private List<ContentItem> content;
        @JsonProperty("callback_url")
        private String callbackUrl;
        @JsonProperty("return_last_frame")
        private Boolean returnLastFrame;
        @JsonProperty("service_tier")
        private String serviceTier;
        @JsonProperty("execution_expires_after")
        private Integer executionExpiresAfter;
        @JsonProperty("generate_audio")
        private Boolean generateAudio;
        private Boolean draft;
        private List<ToolItem> tools;
        private String resolution;
        private String ratio;
        private Integer duration;
        private Integer frames;
        private Integer seed;
        @JsonProperty("camera_fixed")
        private Boolean cameraFixed;
        private Boolean watermark;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolItem {
        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponsePayload {
        private String id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseTask {
        private String id;
        private String model;
        private String status;
        private ResponseContent content;
        private Integer seed;
        private String resolution;
        private Integer duration;
        private String ratio;
        @JsonProperty("framespersecond")
        private Integer framesPerSecond;
        @JsonProperty("service_tier")
        private String serviceTier;
        private List<ToolItem> tools;
        private Usage usage;
        private ErrorInfo error;
        @JsonProperty("created_at")
        private Long createdAt;
        @JsonProperty("updated_at")
        private Long updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseContent {
        @JsonProperty("video_url")
        private String videoUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("completion_tokens")
        private int completionTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;
        @JsonProperty("tool_usage")
        private ToolUsage toolUsage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolUsage {
        @JsonProperty("web_search")
        private int webSearch;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorInfo {
        private String code;
        private String message;
    }
}
