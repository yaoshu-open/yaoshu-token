package yaoshu.token.pojo.dto;

import lombok.Data;

/**
 * Claude 错误结构  */
@Data
public class ClaudeError {

    private String type;
    private String message;
}
