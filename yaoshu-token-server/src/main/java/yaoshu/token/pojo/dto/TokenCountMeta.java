package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Token 计数元数据  */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenCountMeta {

    private String tokenType;
    private String combineText;
    private int toolsCount;
    private int nameCount;
    private int messagesCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FileMeta> files;
    private int maxTokens;
    private double imagePriceRatio;
}
