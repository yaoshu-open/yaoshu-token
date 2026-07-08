package yaoshu.token.relay.channel.zhipu4v;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import yaoshu.token.pojo.dto.OpenAIResponseDTO.OpenAITextResponseChoice;
import yaoshu.token.pojo.dto.Usage;

import java.util.List;

/**
 * 智谱 4V 渠道 DTO 定义  * <p>
 * 智谱 GLM-4V 系列模型，使用 OpenAI 兼容格式。
 */
public class Zhipu4VDTOPlaceholder {

    /**
     * 智谱 V4 响应      */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZhipuV4Response {
        private String id;
        private long created;
        private String model;
        @JsonProperty("choices")
        private List<OpenAITextResponseChoice> textResponseChoices;
        private Usage usage;
        private Object error;
    }
}
