package yaoshu.token.relay.channel.ali;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 阿里渠道 DTO 定义  * <p>
 * 包含阿里通义千问 / 通义万相 / Rerank 的请求与响应 DTO。
 */
public class AliDTOPlaceholder {

    // ======================== Chat 请求 ========================

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliMessage {
        private Object content;
        private String role;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliMediaContent {
        @JsonProperty("image")
        private String image;
        @JsonProperty("text")
        private String text;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliInput {
        private String prompt;
        private List<AliMessage> messages;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliParameters {
        @JsonProperty("top_p")
        private Double topP;
        @JsonProperty("top_k")
        private Integer topK;
        private Long seed;
        @JsonProperty("enable_search")
        private Boolean enableSearch;
        @JsonProperty("incremental_output")
        private Boolean incrementalOutput;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliChatRequest {
        private String model;
        private AliInput input;
        private AliParameters parameters;
    }

    // ======================== Embedding ========================

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliEmbeddingRequest {
        private String model;
        private AliEmbeddingInput input;
        private AliEmbeddingParameters parameters;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliEmbeddingInput {
        private List<String> texts;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliEmbeddingParameters {
        @JsonProperty("text_type")
        private String textType;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliEmbedding {
        private List<Double> embedding;
        @JsonProperty("text_index")
        private int textIndex;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliEmbeddingResponse {
        private AliEmbeddingOutput output;
        private AliUsage usage;
        private String code;
        private String message;
        @JsonProperty("request_id")
        private String requestId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliEmbeddingOutput {
        private List<AliEmbedding> embeddings;
    }

    // ======================== Usage & Error ========================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliUsage {
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outputTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;
        @JsonProperty("image_count")
        private int imageCount;
    }

    // ======================== Image 异步任务 ========================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskResult {
        @JsonProperty("b64_image")
        private String b64Image;
        private String url;
        private String code;
        private String message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliOutput {
        @JsonProperty("task_id")
        private String taskId;
        @JsonProperty("task_status")
        private String taskStatus;
        private String text;
        @JsonProperty("finish_reason")
        private String finishReason;
        private String message;
        private String code;
        private List<TaskResult> results;
        private List<AliOutputChoice> choices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliOutputChoice {
        @JsonProperty("finish_reason")
        private String finishReason;
        private AliOutputMessage message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliOutputMessage {
        private String role;
        private List<AliMediaContent> content;
        @JsonProperty("reasoning_content")
        private String reasoningContent;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliResponse {
        private AliOutput output;
        private AliUsage usage;
        private String code;
        private String message;
        @JsonProperty("request_id")
        private String requestId;
    }

    // ======================== Image 请求 ========================

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliImageRequest {
        private String model;
        private Object input;
        private AliImageParameters parameters;
        @JsonProperty("response_format")
        private String responseFormat;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliImageParameters {
        private String size;
        private Integer n;
        private String steps;
        private String scale;
        private Boolean watermark;
        @JsonProperty("prompt_extend")
        private Boolean promptExtend;
        @JsonProperty("thinking_mode")
        private Boolean thinkingMode;
        @JsonProperty("enable_sequential")
        private Boolean enableSequential;
        @JsonProperty("bbox_list")
        private Object bboxList;
        @JsonProperty("color_palette")
        private Object colorPalette;
        private Integer seed;

        /** */
        public boolean promptExtendValue() {
            return Boolean.TRUE.equals(promptExtend);
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliImageInput {
        private String prompt;
        @JsonProperty("negative_prompt")
        private String negativePrompt;
        private List<AliMessage> messages;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WanImageInput {
        private String prompt;
        private List<String> images;
        @JsonProperty("negative_prompt")
        private String negativePrompt;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WanImageParameters {
        private Integer n;
        private Boolean watermark;
        private Integer seed;
        private Double strength;
    }

    // ======================== Rerank ========================

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliRerankParameters {
        @JsonProperty("top_n")
        private Integer topN;
        @JsonProperty("return_documents")
        private Boolean returnDocuments;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliRerankInput {
        private String query;
        private Object documents;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AliRerankRequest {
        private String model;
        private AliRerankInput input;
        private AliRerankParameters parameters;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliRerankResponse {
        private AliRerankOutput output;
        private AliUsage usage;
        @JsonProperty("request_id")
        private String requestId;
        private String code;
        private String message;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AliRerankOutput {
        private List<Object> results;
    }
}
