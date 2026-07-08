package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Rerank 响应体  */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerankResponse {
    private List<RerankResponseResult> results;
    private Usage usage;
}
