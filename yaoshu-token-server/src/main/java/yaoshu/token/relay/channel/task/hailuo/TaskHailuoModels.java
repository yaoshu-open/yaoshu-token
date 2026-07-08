package yaoshu.token.relay.channel.task.hailuo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Hailuo（海螺 AI / MiniMax 视频）模型与 DTO 定义，
 */
public class TaskHailuoModels {

    // ======================== 常量 ========================

    public static final String CHANNEL_NAME = "hailuo-video";

    public static final String TEXT_TO_VIDEO_ENDPOINT = "/v1/video_generation";
    public static final String QUERY_TASK_ENDPOINT = "/v1/query/video_generation";

    // 状态码
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_RATE_LIMIT = 1002;
    public static final int STATUS_AUTH_FAILED = 1004;
    public static final int STATUS_NO_BALANCE = 1008;
    public static final int STATUS_SENSITIVE = 1026;
    public static final int STATUS_PARAM_ERROR = 2013;
    public static final int STATUS_INVALID_KEY = 2049;

    // 任务状态
    public static final String TASK_STATUS_PREPARING = "Preparing";
    public static final String TASK_STATUS_QUEUEING = "Queueing";
    public static final String TASK_STATUS_PROCESSING = "Processing";
    public static final String TASK_STATUS_SUCCESS = "Success";
    public static final String TASK_STATUS_FAILED = "Fail";

    // 分辨率
    public static final String RESOLUTION_512P = "512P";
    public static final String RESOLUTION_720P = "720P";
    public static final String RESOLUTION_768P = "768P";
    public static final String RESOLUTION_1080P = "1080P";

    public static final int DEFAULT_DURATION = 6;
    public static final String DEFAULT_RESOLUTION = RESOLUTION_720P;

    public static final List<String> MODEL_LIST = List.of(
            "MiniMax-Hailuo-2.3",
            "MiniMax-Hailuo-2.3-Fast",
            "MiniMax-Hailuo-02",
            "T2V-01-Director",
            "T2V-01",
            "I2V-01-Director",
            "I2V-01-live",
            "I2V-01",
            "S2V-01"
    );

    // ======================== DTO ========================

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SubjectReference {
        private String type;
        private List<String> image;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VideoRequest {
        private String model;
        private String prompt;
        @JsonProperty("prompt_optimizer")
        private Boolean promptOptimizer;
        @JsonProperty("fast_pretreatment")
        private Boolean fastPretreatment;
        private Integer duration;
        private String resolution;
        @JsonProperty("callback_url")
        private String callbackURL;
        @JsonProperty("aigc_watermark")
        private Boolean aigcWatermark;
        @JsonProperty("first_frame_image")
        private String firstFrameImage;
        @JsonProperty("last_frame_image")
        private String lastFrameImage;
        @JsonProperty("subject_reference")
        private List<SubjectReference> subjectReference;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BaseResp {
        @JsonProperty("status_code")
        private int statusCode;
        @JsonProperty("status_msg")
        private String statusMsg;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoResponse {
        @JsonProperty("task_id")
        private String taskId;
        private BaseResp baseResp;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QueryTaskRequest {
        @JsonProperty("task_id")
        private String taskId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryTaskResponse {
        @JsonProperty("task_id")
        private String taskId;
        private String status;
        @JsonProperty("file_id")
        private String fileId;
        @JsonProperty("video_width")
        private int videoWidth;
        @JsonProperty("video_height")
        private int videoHeight;
        private BaseResp baseResp;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileObject {
        @JsonProperty("file_id")
        private long fileId;
        private long bytes;
        @JsonProperty("created_at")
        private long createdAt;
        private String filename;
        private String purpose;
        @JsonProperty("download_url")
        private String downloadUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RetrieveFileResponse {
        private FileObject file;
        private BaseResp baseResp;
    }

    // ======================== 模型配置 ========================

    @Data
    public static class ModelConfig {
        private String name;
        private String defaultResolution;
        private List<Integer> supportedDurations;
        private List<String> supportedResolutions;
        private boolean hasPromptOptimizer;
        private boolean hasFastPretreatment;
    }

    /**
     * 获取模型配置      */
    public static ModelConfig getModelConfig(String model) {
        return switch (model) {
            case "MiniMax-Hailuo-2.3", "MiniMax-Hailuo-2.3-Fast" -> createConfig(model,
                    RESOLUTION_768P, List.of(6, 10), List.of(RESOLUTION_768P, RESOLUTION_1080P), true, true);
            case "MiniMax-Hailuo-02" -> createConfig(model,
                    RESOLUTION_768P, List.of(6, 10), List.of(RESOLUTION_512P, RESOLUTION_768P, RESOLUTION_1080P), true, true);
            case "T2V-01-Director" -> createConfig(model,
                    RESOLUTION_768P, List.of(6), List.of(RESOLUTION_768P, RESOLUTION_1080P), true, false);
            case "T2V-01" -> createConfig(model,
                    RESOLUTION_720P, List.of(6), List.of(RESOLUTION_720P), true, false);
            case "I2V-01-Director", "I2V-01-live", "I2V-01" -> createConfig(model,
                    RESOLUTION_720P, List.of(6), List.of(RESOLUTION_720P, RESOLUTION_1080P), true, false);
            case "S2V-01" -> createConfig(model,
                    RESOLUTION_720P, List.of(6), List.of(RESOLUTION_720P), true, false);
            default -> createConfig(model,
                    DEFAULT_RESOLUTION, List.of(DEFAULT_DURATION), List.of(DEFAULT_RESOLUTION), true, false);
        };
    }

    private static ModelConfig createConfig(String name, String defaultRes,
                                            List<Integer> durations, List<String> resolutions,
                                            boolean hasPromptOpt, boolean hasFastPretreat) {
        ModelConfig config = new ModelConfig();
        config.setName(name);
        config.setDefaultResolution(defaultRes);
        config.setSupportedDurations(durations);
        config.setSupportedResolutions(resolutions);
        config.setHasPromptOptimizer(hasPromptOpt);
        config.setHasFastPretreatment(hasFastPretreat);
        return config;
    }
}
