package yaoshu.token.config.operation;

import lombok.Data;
import java.util.Map;

/**
 * HTTP 状态码范围配置  */
@Data
public class StatusCodeRangesConfig {

    /** 成功状态码范围 [min, max] */
    private Map<String, int[]> successRanges;

    /** 是否启用状态码范围判定 */
    private boolean enabled;

    /** 检查状态码是否为成功状态 */
    public boolean isSuccess(int statusCode) {
        if (!enabled || successRanges == null) {
            return statusCode >= 200 && statusCode < 300;
        }
        for (int[] range : successRanges.values()) {
            if (statusCode >= range[0] && statusCode <= range[1]) return true;
        }
        return false;
    }
}
