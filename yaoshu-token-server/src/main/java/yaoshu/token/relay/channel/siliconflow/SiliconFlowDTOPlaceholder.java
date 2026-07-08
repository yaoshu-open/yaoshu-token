package yaoshu.token.relay.channel.siliconflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * SiliconFlow 渠道 DTO 定义  */
public class SiliconFlowDTOPlaceholder {

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SFTokens {
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outputTokens;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SFMeta {
        private SFTokens tokens;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SFRerankResponse {
        private java.util.List<Object> results;
        private SFMeta meta;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SFImageRequest {
        private String model;
        private String prompt;
        @JsonProperty("negative_prompt")
        private String negativePrompt;
        @JsonProperty("image_size")
        private String imageSize;
        @JsonProperty("batch_size")
        private Integer batchSize;
        private Long seed;
        @JsonProperty("num_inference_steps")
        private Integer numInferenceSteps;
        @JsonProperty("guidance_scale")
        private Double guidanceScale;
        private Double cfg;
        private String image;
        @JsonProperty("image2")
        private String image2;
        @JsonProperty("image3")
        private String image3;
    }
}
