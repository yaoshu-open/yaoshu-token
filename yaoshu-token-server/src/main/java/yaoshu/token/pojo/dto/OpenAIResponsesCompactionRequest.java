package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI Responses Compaction 请求体  * <p>
 * 用于 /v1/responses/compact 端点，将上轮对话压缩为摘要。
 *
 * @author yaoshu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAIResponsesCompactionRequest {

    private String model;
    private Object input;
    private Object instructions;

    @JsonProperty("previous_response_id")
    private String previousResponseId;
}
