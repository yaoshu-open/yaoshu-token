package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Rerank 响应结果项  */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerankResponseResult {
    private Object document;
    private int index;
    @JsonProperty("relevance_score")
    private double relevanceScore;
}
