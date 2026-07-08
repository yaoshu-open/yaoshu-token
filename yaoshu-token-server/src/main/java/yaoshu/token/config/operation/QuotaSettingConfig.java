package yaoshu.token.config.operation;

import lombok.Data;

/**
 * 配额设置 POJO  */
@Data
public class QuotaSettingConfig {

    /** 每 1 USD 配额（默认 500） */
    private double quotaPerUnit = 500_000;

    /** 新用户注册配额 */
    private int quotaForNewUser = 500;

    /** 邀请人配额 */
    private int quotaForInviter = 1000;

    /** 被邀请人配额 */
    private int quotaForInvitee = 500;

    /** 渠道自动禁用阈值 */
    private double channelDisableThreshold = 5.0;

    /** 是否启用渠道自动禁用 */
    private boolean automaticDisableChannelEnabled;

    /** 是否启用渠道自动启用 */
    private boolean automaticEnableChannelEnabled;

    /** 配额提醒阈值 */
    private int quotaRemindThreshold = 1000;

    /** 预消费配额 */
    private int preConsumedQuota = 500;

    /** 信任配额（超过此值不预扣） */
    private int trustQuota;
}
