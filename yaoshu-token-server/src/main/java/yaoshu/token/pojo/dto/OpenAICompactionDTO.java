package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI 压缩响应 DTO  *
 * @author yaoshu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAICompactionDTO {

    private String id;
    private String object;

    @JsonProperty("created_at")
    private Long createdAt;

    @JsonProperty("output")
    private Object output;

    private Object usage;
    private Object error;
}
