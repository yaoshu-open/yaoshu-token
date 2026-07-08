package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

/**
 * OpenAI 标准错误结构  */
@Data
public class OpenAIError {

    private String message;
    private String type;
    private String param;
    private Object code;
    @JsonRawValue
    private String metadata;
}
