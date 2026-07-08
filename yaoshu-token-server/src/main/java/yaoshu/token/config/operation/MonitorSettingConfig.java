package yaoshu.token.config.operation;

import lombok.Data;

/**
 * 监控设置 POJO  */
@Data
public class MonitorSettingConfig {

    /** 是否启用监控 */
    private boolean enabled;

    /** 监控间隔（秒） */
    private int intervalSeconds = 60;

    /** 失败阈值 */
    private int failureThreshold = 5;

    /** 通知方式 */
    private String notifyMethod;
}
