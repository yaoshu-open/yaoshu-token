package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Cohere / Jina Rerank 请求体  */
@Data
public class RerankRequest {
    private String model;
    private String query;
    private Object documents;
    private int topN;

    @JsonProperty("return_documents")
    private Boolean returnDocuments;

    public boolean getReturnDocuments() {
        return returnDocuments != null && returnDocuments;
    }
}
