package yaoshu.token.relay.channel.xai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponseChoice;
import yaoshu.token.pojo.dto.Usage;

import java.util.List;

/**
 * XAI（Grok）渠道 DTO 定义  */
public class XAIDTOPlaceholder {

    /**
     * XAI Chat Completion 响应      */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatCompletionResponse {
        private String id;
        private String object;
        private long created;
        private String model;
        private List<OpenAITextResponseChoice> choices;
        private Usage usage;
        @JsonProperty("system_fingerprint")
        private String systemFingerprint;
    }

    /**
     * XAI 图片请求      * <p>
     * xAI 不支持 quality/size/style 参数。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageRequest {
        private String model;
        private String prompt;
        private Integer n;
        @JsonProperty("response_format")
        private String responseFormat;
    }
}
