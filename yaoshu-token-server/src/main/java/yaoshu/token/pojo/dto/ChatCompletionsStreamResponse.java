package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 流式响应  */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionsStreamResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private String systemFingerprint;
    private Usage usage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private int index;
        private Delta delta;
        private Object logprobs;
        private String finishReason;
        private Object contentFilterResults;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {
        private String role;
        private String content;
        private String reasoningContent;
        private List<Object> toolCalls;
        private String refusal;
        private Map<String, Object> functionCall;
    }
}
