package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 视频 DTO  *
 * @author yaoshu
 */
public class OpenAIVideoDTO {

    public static final String VIDEO_STATUS_UNKNOWN = "unknown";
    public static final String VIDEO_STATUS_QUEUED = "queued";
    public static final String VIDEO_STATUS_IN_PROGRESS = "in_progress";
    public static final String VIDEO_STATUS_COMPLETED = "completed";
    public static final String VIDEO_STATUS_FAILED = "failed";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAIVideo {
        private String id;

        @JsonProperty("task_id")
        private String taskId;

        private String object;
        private String model;
        private String status;
        private Integer progress;

        @JsonProperty("created_at")
        private Long createdAt;

        @JsonProperty("completed_at")
        private Long completedAt;

        @JsonProperty("expires_at")
        private Long expiresAt;

        private String seconds;
        private String size;

        @JsonProperty("remixed_from_video_id")
        private String remixedFromVideoId;

        private OpenAIVideoError error;
        private Object metadata;

        public void setProgressStr(String progress) {
            if (progress != null && progress.endsWith("%")) {
                progress = progress.substring(0, progress.length() - 1);
            }
            try {
                this.progress = Integer.parseInt(progress);
            } catch (NumberFormatException e) {
                this.progress = 0;
            }
        }

        @SuppressWarnings("unchecked")
        public void setMetadata(String k, Object v) {
            if (metadata == null) {
                metadata = new java.util.HashMap<String, Object>();
            }
            ((java.util.Map<String, Object>) metadata).put(k, v);
        }

        public static OpenAIVideo newOpenAIVideo() {
            return OpenAIVideo.builder()
                    .object("video")
                    .status(VIDEO_STATUS_QUEUED)
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAIVideoError {
        private String message;
        private String code;
    }
}
