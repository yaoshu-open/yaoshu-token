package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 渠道信息 DTO  *
 * @author yaoshu
 */
@Data
public class ChannelInfoDTO {

    /** 是否多Key模式 */
    @JsonProperty("is_multi_key")
    private boolean isMultiKey;

    /** 多Key模式下的Key数量 */
    @JsonProperty("multi_key_size")
    private int multiKeySize;

    /** key状态列表，key index -> status */
    @JsonProperty("multi_key_status_list")
    private Map<Integer, Integer> multiKeyStatusList;

    /** key禁用原因列表，key index -> reason */
    @JsonProperty("multi_key_disabled_reason")
    private Map<Integer, String> multiKeyDisabledReason;

    /** key禁用时间列表，key index -> time */
    @JsonProperty("multi_key_disabled_time")
    private Map<Integer, Long> multiKeyDisabledTime;

    /** 多Key模式下轮询的key索引 */
    @JsonProperty("multi_key_polling_index")
    private int multiKeyPollingIndex;

    /** 多Key模式：random / polling */
    @JsonProperty("multi_key_mode")
    private String multiKeyMode;
}
