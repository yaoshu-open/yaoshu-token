package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Gemini API 请求/响应 DTO  *
 * @author yaoshu
 */
public class GeminiDTO {

    // ===== Chat 请求 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiChatRequest {
        private List<GeminiChatRequest> requests;

        @JsonProperty("contents")
        private List<GeminiChatContent> contents;

        @JsonProperty("safetySettings")
        private List<GeminiChatSafetySettings> safetySettings;

        @JsonProperty("generationConfig")
        private GeminiChatGenerationConfig generationConfig;

        @JsonProperty("tools")
        private Object tools;

        @JsonProperty("toolConfig")
        private ToolConfig toolConfig;

        @JsonProperty("systemInstruction")
        private GeminiChatContent systemInstructions;

        @JsonProperty("cachedContent")
        private String cachedContent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiChatContent {
        private String role;
        private List<GeminiPart> parts;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiPart {
        private String text;
        private Boolean thought;

        @JsonProperty("inlineData")
        private GeminiInlineData inlineData;

        @JsonProperty("functionCall")
        private FunctionCall functionCall;

        @JsonProperty("thoughtSignature")
        private Object thoughtSignature;

        @JsonProperty("functionResponse")
        private GeminiFunctionResponse functionResponse;

        @JsonProperty("mediaResolution")
        private Object mediaResolution;

        @JsonProperty("videoMetadata")
        private Object videoMetadata;

        @JsonProperty("fileData")
        private GeminiFileData fileData;

        @JsonProperty("executableCode")
        private GeminiPartExecutableCode executableCode;

        @JsonProperty("codeExecutionResult")
        private GeminiPartCodeExecutionResult codeExecutionResult;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiInlineData {
        @JsonProperty("mimeType")
        private String mimeType;

        private String data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiFileData {
        @JsonProperty("mimeType")
        private String mimeType;

        @JsonProperty("fileUri")
        private String fileUri;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private Object args;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiFunctionResponse {
        private String name;
        private Object response;

        @JsonProperty("willContinue")
        private Object willContinue;

        @JsonProperty("scheduling")
        private Object scheduling;

        @JsonProperty("parts")
        private Object parts;

        @JsonProperty("id")
        private Object id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiPartExecutableCode {
        private String language;
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiPartCodeExecutionResult {
        private String outcome;
        private String output;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiChatSafetySettings {
        private String category;
        private String threshold;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolConfig {
        @JsonProperty("functionCallingConfig")
        private FunctionCallingConfig functionCallingConfig;

        @JsonProperty("retrievalConfig")
        private RetrievalConfig retrievalConfig;

        @JsonProperty("includeServerSideToolInvocations")
        private Boolean includeServerSideToolInvocations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCallingConfig {
        private String mode;

        @JsonProperty("allowedFunctionNames")
        private List<String> allowedFunctionNames;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalConfig {
        private LatLng latLng;

        @JsonProperty("languageCode")
        private String languageCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatLng {
        private Double latitude;
        private Double longitude;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiChatGenerationConfig {
        @JsonProperty("temperature")
        private Double temperature;

        @JsonProperty("topP")
        private Double topP;

        @JsonProperty("topK")
        private Double topK;

        @JsonProperty("maxOutputTokens")
        private Integer maxOutputTokens;

        @JsonProperty("candidateCount")
        private Integer candidateCount;

        @JsonProperty("stopSequences")
        private List<String> stopSequences;

        @JsonProperty("responseMimeType")
        private String responseMimeType;

        @JsonProperty("responseSchema")
        private Object responseSchema;

        @JsonProperty("responseJsonSchema")
        private Object responseJsonSchema;

        @JsonProperty("presencePenalty")
        private Float presencePenalty;

        @JsonProperty("frequencyPenalty")
        private Float frequencyPenalty;

        @JsonProperty("responseLogprobs")
        private Boolean responseLogprobs;

        @JsonProperty("logprobs")
        private Integer logprobs;

        @JsonProperty("enableEnhancedCivicAnswers")
        private Boolean enableEnhancedCivicAnswers;

        @JsonProperty("mediaResolution")
        private String mediaResolution;

        @JsonProperty("seed")
        private Long seed;

        @JsonProperty("responseModalities")
        private List<String> responseModalities;

        @JsonProperty("thinkingConfig")
        private GeminiThinkingConfig thinkingConfig;

        @JsonProperty("speechConfig")
        private Object speechConfig;

        @JsonProperty("imageConfig")
        private Object imageConfig;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiThinkingConfig {
        @JsonProperty("includeThoughts")
        private Boolean includeThoughts;

        @JsonProperty("thinkingBudget")
        private Integer thinkingBudget;

        @JsonProperty("thinkingLevel")
        private String thinkingLevel;
    }

    // ===== Chat 响应 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiChatResponse {
        @JsonProperty("candidates")
        private List<GeminiChatCandidate> candidates;

        @JsonProperty("promptFeedback")
        private GeminiChatPromptFeedback promptFeedback;

        @JsonProperty("usageMetadata")
        private GeminiUsageMetadata usageMetadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiChatCandidate {
        private GeminiChatContent content;

        @JsonProperty("finishReason")
        private String finishReason;

        private Long index;

        @JsonProperty("safetyRatings")
        private List<GeminiChatSafetyRating> safetyRatings;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiChatSafetyRating {
        private String category;
        private String probability;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiChatPromptFeedback {
        @JsonProperty("safetyRatings")
        private List<GeminiChatSafetyRating> safetyRatings;

        @JsonProperty("blockReason")
        private String blockReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiUsageMetadata {
        @JsonProperty("promptTokenCount")
        private Integer promptTokenCount;

        @JsonProperty("toolUsePromptTokenCount")
        private Integer toolUsePromptTokenCount;

        @JsonProperty("candidatesTokenCount")
        private Integer candidatesTokenCount;

        @JsonProperty("totalTokenCount")
        private Integer totalTokenCount;

        @JsonProperty("thoughtsTokenCount")
        private Integer thoughtsTokenCount;

        @JsonProperty("cachedContentTokenCount")
        private Integer cachedContentTokenCount;

        @JsonProperty("promptTokensDetails")
        private List<GeminiPromptTokensDetails> promptTokensDetails;

        @JsonProperty("toolUsePromptTokensDetails")
        private List<GeminiPromptTokensDetails> toolUsePromptTokensDetails;

        @JsonProperty("candidatesTokensDetails")
        private List<GeminiPromptTokensDetails> candidatesTokensDetails;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiPromptTokensDetails {
        private String modality;

        @JsonProperty("tokenCount")
        private Integer tokenCount;
    }

    // ===== Image 请求/响应 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiImageRequest {
        private List<GeminiImageInstance> instances;
        private GeminiImageParameters parameters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiImageInstance {
        private String prompt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiImageParameters {
        @JsonProperty("sampleCount")
        private Integer sampleCount;

        @JsonProperty("aspectRatio")
        private String aspectRatio;

        @JsonProperty("personGeneration")
        private String personGeneration;

        @JsonProperty("imageSize")
        private String imageSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiImageResponse {
        private List<GeminiImagePrediction> predictions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiImagePrediction {
        @JsonProperty("mimeType")
        private String mimeType;

        @JsonProperty("bytesBase64Encoded")
        private String bytesBase64Encoded;

        @JsonProperty("raiFilteredReason")
        private String raiFilteredReason;

        @JsonProperty("safetyAttributes")
        private Object safetyAttributes;
    }

    // ===== Embedding 请求/响应 =====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiEmbeddingRequest {
        private String model;
        private GeminiChatContent content;

        @JsonProperty("taskType")
        private String taskType;

        private String title;

        @JsonProperty("outputDimensionality")
        private Integer outputDimensionality;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiBatchEmbeddingRequest {
        private List<GeminiEmbeddingRequest> requests;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiEmbeddingResponse {
        private ContentEmbedding embedding;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiBatchEmbeddingResponse {
        private List<ContentEmbedding> embeddings;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentEmbedding {
        private List<Double> values;
    }
}
