package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订阅订单实体  *
 * @author yaoshu
 */
@Data
@TableName("subscription_orders")
public class SubscriptionOrder {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer planId;

    private Double money;

    private String tradeNo;

    private String paymentMethod;

    private String paymentProvider;

    private String status;

    private Long createTime;

    private Long completeTime;

    private String providerPayload;
}
