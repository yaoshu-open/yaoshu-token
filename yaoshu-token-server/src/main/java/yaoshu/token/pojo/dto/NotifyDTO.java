package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知 DTO  *
 * @author yaoshu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifyDTO {

    /** 通知内容值占位符 */
    public static final String CONTENT_VALUE_PARAM = "{{value}}";

    /** 配额超限通知 */
    public static final String NOTIFY_TYPE_QUOTA_EXCEED = "quota_exceed";
    /** 渠道更新通知 */
    public static final String NOTIFY_TYPE_CHANNEL_UPDATE = "channel_update";
    /** 渠道测试通知 */
    public static final String NOTIFY_TYPE_CHANNEL_TEST = "channel_test";

    private String type;
    private String title;
    private String content;

    @JsonProperty("values")
    private Object[] values;
}
