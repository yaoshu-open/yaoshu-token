package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订阅预消费记录实体  *
 * @author yaoshu
 */
@Data
@TableName("subscription_pre_consume_records")
public class SubscriptionPreConsumeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private Integer userId;

    private Integer userSubscriptionId;

    /** 预扣额度 */
    private Long preConsumed;

    /** 状态：consumed / refunded */
    private String status;

    private Integer planId;

    /** 兼容字段（Go 中部分场景使用） */
    private Long amount;
    private String model;

    private Integer tokenId;

    private Long createdAt;

    private Long updatedAt;
}
