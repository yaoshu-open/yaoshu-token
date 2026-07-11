package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户订阅实体  *
 * @author yaoshu
 */
@Data
@TableName("user_subscriptions")
public class UserSubscription {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer planId;

    private Long amountTotal;

    private Long amountUsed;

    private Long startTime;

    private Long endTime;

    private String status;
    
    /**
     * 类型：subscription(订阅) / free_trial(免费额度)
     */
    private String type;
    
    /**
     * 模型白名单JSON数组，仅type=free_trial时有效
     */
    private String modelWhitelist;
    
    private String source;

    /**
     * 是否自动续期：true=自动续期，false=已关闭续期（到期不续），默认 true
     */
    private Boolean autoRenew;

    private Long lastResetTime;

    private Long nextResetTime;

    private String upgradeGroup;

    private String prevUserGroup;

    private Long createdAt;

    private Long updatedAt;
}
