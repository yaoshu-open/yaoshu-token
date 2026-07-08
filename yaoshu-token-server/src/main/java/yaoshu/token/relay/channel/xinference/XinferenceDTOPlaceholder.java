package yaoshu.token.relay.channel.xinference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Xinference 渠道 DTO 定义  * <p>
 * Xinference 是 OpenAI 兼容协议，仅有 Rerank 响应有自定义格式。
 */
public class XinferenceDTOPlaceholder {

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XinRerankResponseDocument {
        private Object document;
        private int index;
        @JsonProperty("relevance_score")
        private double relevanceScore;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class XinRerankResponse {
        private java.util.List<XinRerankResponseDocument> results;
    }
}
