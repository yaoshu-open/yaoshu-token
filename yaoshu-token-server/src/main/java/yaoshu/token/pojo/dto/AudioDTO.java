package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 音频相关 DTO  *
 * @author yaoshu
 */
public class AudioDTO {

    // Top-level responseFormat（relay 代码直接使用 AudioDTO 而非 AudioRequest）
    private String responseFormat;

    public String getResponseFormat() { return responseFormat; }
    public void setResponseFormat(String v) { this.responseFormat = v; }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioRequest {
        private String model;
        private String input;
        private String voice;
        private String instructions;

        @JsonProperty("response_format")
        private String responseFormat;

        private Double speed;

        @JsonProperty("stream_format")
        private String streamFormat;

        private Object metadata;

        // vllm-omini
        @JsonProperty("task_type")
        private Object taskType;
        private Object language;
        @JsonProperty("ref_audio")
        private Object refAudio;
        @JsonProperty("ref_text")
        private Object refText;
        @JsonProperty("x_vector_only_mode")
        private Object xVectorOnlyMode;
        @JsonProperty("max_new_tokens")
        private Object maxNewTokens;
        @JsonProperty("initial_codec_chunk_frames")
        private Object initialCodecChunkFrames;

        public boolean isStream() {
            return "sse".equals(streamFormat);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioResponse {
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WhisperVerboseJSONResponse {
        private String task;
        private String language;
        private Double duration;
        private String text;
        private List<Segment> segments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        private Integer id;
        private Integer seek;
        private Double start;
        private Double end;
        private String text;
        private List<Integer> tokens;
        private Double temperature;

        @JsonProperty("avg_logprob")
        private Double avgLogprob;

        @JsonProperty("compression_ratio")
        private Double compressionRatio;

        @JsonProperty("no_speech_prob")
        private Double noSpeechProb;
    }
}
