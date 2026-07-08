package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 错误响应 DTO  * <p>
 * 通用错误响应结构，兼容 OpenAI 及多种上游 API 的错误格式。
 *
 * @author yaoshu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDTO {

    @JsonProperty("error")
    private String error;

    @JsonProperty("message")
    private String message;

    @JsonProperty("msg")
    private String msg;

    @JsonProperty("err")
    private String err;

    @JsonProperty("error_msg")
    private String errorMsg;

    @JsonProperty("detail")
    private String detail;

    @JsonProperty("status_code")
    private Integer statusCode;

    @JsonProperty("local_error")
    private Boolean localError;
}
