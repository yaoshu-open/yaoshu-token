package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订阅计划实体  *
 * @author yaoshu
 */
@Data
@TableName("subscription_plans")
public class SubscriptionPlan {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String title;
    private String subtitle;

    private Double priceAmount;

    private String currency;

    private String durationUnit;

    private Integer durationValue;

    private Long customSeconds;

    private Boolean enabled;

    private Integer sortOrder;

    private Boolean allowBalancePay;

    private String stripePriceId;

    private String creemProductId;

    private String waffoPancakeProductId;

    private Integer maxPurchasePerUser;

    private String upgradeGroup;

    private Long totalAmount;

    private String quotaResetPeriod;

    private Long quotaResetCustomSeconds;

    private Long createdAt;

    private Long updatedAt;
}
