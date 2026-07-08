package yaoshu.token.pojo.dto;

import lombok.Data;

/**
 * 能力+渠道信息 DTO  * <p>
 * 用于 Pricing 计算时获取所有启用能力及其对应的渠道类型。
 */
@Data
public class AbilityWithChannel {

    /** 分组名 */
    private String group;

    /** 模型名 */
    private String model;

    /** 渠道 ID */
    private Integer channelId;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先级 */
    private Long priority;

    /** 权重 */
    private Integer weight;

    /** 标签 */
    private String tag;

    /** 渠道类型（来自 channels.type LEFT JOIN） */
    private Integer channelType;
}
