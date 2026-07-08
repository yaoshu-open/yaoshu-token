package yaoshu.token.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求元数据（日志/计费用）  */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestMeta {

    private String originalModelName;
    private String userUsingGroup;
    private int promptTokens;
    private int preConsumedQuota;
}
