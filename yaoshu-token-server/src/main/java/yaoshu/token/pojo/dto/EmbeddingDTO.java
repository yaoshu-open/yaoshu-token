package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Embedding 相关 DTO  * <p>
 * 直接可用字段适配 EmbeddingRequest 场景。
 * 注意：relay 代码直接使用 EmbeddingDTO 而非 EmbeddingRequest 内部类。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingDTO {

    // ======================== Top-level 字段（relay 代码直接使用） ========================

    private String model;
    private Object input;
    private Integer dimensions;

    @JsonProperty("encoding_format")
    private String encodingFormat;

    private String user;
    private Double seed;
    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    // ======================== 内部类 ========================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingRequest {
        private String model;
        private Object input;
        private Integer dimensions;
        @JsonProperty("encoding_format")
        private String encodingFormat;
        private String user;
        private Double seed;
        private Double temperature;
        @JsonProperty("top_p")
        private Double topP;
        @JsonProperty("frequency_penalty")
        private Double frequencyPenalty;
        @JsonProperty("presence_penalty")
        private Double presencePenalty;

        public boolean isStream() { return false; }

        @SuppressWarnings("unchecked")
        public List<String> parseInput() {
            if (input == null) return List.of();
            if (input instanceof String) return List.of((String) input);
            if (input instanceof List) {
                return ((List<Object>) input).stream()
                        .filter(item -> item instanceof String)
                        .map(item -> (String) item)
                        .toList();
            }
            return List.of();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingResponseItem {
        private String object;
        private Integer index;
        private List<Double> embedding;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingResponse {
        private String object;
        private List<EmbeddingResponseItem> data;
        private String model;
        private Object usage;
    }
}
