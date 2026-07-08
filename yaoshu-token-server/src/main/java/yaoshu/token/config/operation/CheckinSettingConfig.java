package yaoshu.token.config.operation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 签到设置 POJO  * <p>
 * 存储于 options 表 key="checkin_setting" 的 JSON 中，运行时由 CheckinService 解析读取。
 */
@Data
public class CheckinSettingConfig {

    /** 是否启用签到功能 */
    private boolean enabled = false;

    /** 签到最小额度奖励（默认 1000，约 0.002 USD） */
    @JsonProperty("min_quota")
    private int minQuota = 1000;

    /** 签到最大额度奖励（默认 10000，约 0.02 USD） */
    @JsonProperty("max_quota")
    private int maxQuota = 10000;
}
