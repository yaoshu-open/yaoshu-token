package yaoshu.token.relay.channel.xunfei;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import yaoshu.token.pojo.dto.Usage;

import java.util.List;

/**
 * 讯飞星火渠道 DTO 定义  * <p>
 * 讯飞星火 API 使用 WebSocket 通信，请求/响应为嵌套 JSON 结构。
 */
public class XunfeiDTOPlaceholder {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XunfeiMessage {
        private String role;
        private String content;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XunfeiChatRequest {
        private XunfeiHeader header;
        private XunfeiParameter parameter;
        private XunfeiPayload payload;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XunfeiHeader {
        @JsonProperty("app_id")
        private String appId;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XunfeiParameter {
        private XunfeiChatParam chat;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XunfeiChatParam {
        private String domain;
        private Double temperature;
        @JsonProperty("top_k")
        private Integer topK;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
        private Boolean auditing;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XunfeiPayload {
        private XunfeiPayloadMessage message;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class XunfeiPayloadMessage {
        private List<XunfeiMessage> text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XunfeiChatResponseTextItem {
        private String content;
        private String role;
        private int index;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XunfeiChatResponse {
        private XunfeiResponseHeader header;
        private XunfeiResponsePayload payload;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XunfeiResponseHeader {
        private int code;
        private String message;
        private String sid;
        private int status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XunfeiResponsePayload {
        private XunfeiResponseChoices choices;
        private XunfeiResponseUsage usage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XunfeiResponseChoices {
        private int status;
        private int seq;
        private List<XunfeiChatResponseTextItem> text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XunfeiResponseUsage {
        private Usage text;
    }
}
