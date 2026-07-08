package yaoshu.token.relay.channel.task.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Task Gemini DTO 定义  * <p>
 * Vertex AI Gemini Veo 视频生成任务的请求/响应 DTO。
 */
public class TaskGeminiDTO {

    /**
     * Veo 图片输入      */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VeoImageInput {
        @JsonProperty("bytesBase64Encoded")
        private String bytesBase64Encoded;
        @JsonProperty("mimeType")
        private String mimeType;
    }

    /**
     * Veo 实例      */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VeoInstance {
        private String prompt;
        private VeoImageInput image;
    }

    /**
     * Veo 参数      */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VeoParameters {
        @JsonProperty("sampleCount")
        private int sampleCount;
        @JsonProperty("durationSeconds")
        private Integer durationSeconds;
        @JsonProperty("aspectRatio")
        private String aspectRatio;
        @JsonProperty("resolution")
        private String resolution;
        @JsonProperty("negativePrompt")
        private String negativePrompt;
        @JsonProperty("personGeneration")
        private String personGeneration;
        @JsonProperty("storageUri")
        private String storageUri;
        @JsonProperty("compressionQuality")
        private String compressionQuality;
        @JsonProperty("resizeMode")
        private String resizeMode;
        @JsonProperty("seed")
        private Integer seed;
        @JsonProperty("generateAudio")
        private Boolean generateAudio;
    }

    /**
     * Veo 请求体      */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VeoRequestPayload {
        private List<VeoInstance> instances;
        private VeoParameters parameters;
    }

    /**
     * 提交响应      */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubmitResponse {
        private String name;
    }

    /**
     * 操作视频项      */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OperationVideo {
        @JsonProperty("mimeType")
        private String mimeType;
        @JsonProperty("bytesBase64Encoded")
        private String bytesBase64Encoded;
        @JsonProperty("encoding")
        private String encoding;
    }

    /**
     * 操作响应      */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OperationResponse {
        private String name;
        private boolean done;
        private OperationResponseBody response;
        private OperationError error;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OperationResponseBody {
        @JsonProperty("@type")
        private String type;
        @JsonProperty("raiMediaFilteredCount")
        private int raiMediaFilteredCount;
        private List<OperationVideo> videos;
        @JsonProperty("bytesBase64Encoded")
        private String bytesBase64Encoded;
        private String encoding;
        private String video;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OperationError {
        private String message;
    }
}
