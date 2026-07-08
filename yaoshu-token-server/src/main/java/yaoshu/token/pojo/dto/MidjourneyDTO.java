package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Midjourney 相关 DTO  *
 * @author yaoshu
 */
public class MidjourneyDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SwapFaceRequest {
        @JsonProperty("sourceBase64")
        private String sourceBase64;
        @JsonProperty("targetBase64")
        private String targetBase64;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MidjourneyRequest {
        private String prompt;
        @JsonProperty("customId")
        private String customId;
        @JsonProperty("botType")
        private String botType;
        @JsonProperty("notifyHook")
        private String notifyHook;
        private String action;
        private Integer index;
        private String state;
        @JsonProperty("taskId")
        private String taskId;
        @JsonProperty("base64Array")
        private List<String> base64Array;
        private String content;
        @JsonProperty("maskBase64")
        private String maskBase64;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MidjourneyResponse {
        private Integer code;
        private String description;
        private Object properties;
        private String result;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MidjourneyUploadResponse {
        private Integer code;
        private String description;
        private List<String> result;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImgUrls {
        private String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MidjourneyTaskDTO {
        private String id;
        private String action;
        @JsonProperty("customId")
        private String customId;
        @JsonProperty("botType")
        private String botType;
        private String prompt;
        @JsonProperty("promptEn")
        private String promptEn;
        private String description;
        private String state;
        @JsonProperty("submitTime")
        private Long submitTime;
        @JsonProperty("startTime")
        private Long startTime;
        @JsonProperty("finishTime")
        private Long finishTime;
        @JsonProperty("imageUrl")
        private String imageUrl;
        @JsonProperty("videoUrl")
        private String videoUrl;
        @JsonProperty("videoUrls")
        private List<ImgUrls> videoUrls;
        private String status;
        private String progress;
        @JsonProperty("failReason")
        private String failReason;
        private Object buttons;
        @JsonProperty("maskBase64")
        private String maskBase64;
        private Object properties;
    }
}
