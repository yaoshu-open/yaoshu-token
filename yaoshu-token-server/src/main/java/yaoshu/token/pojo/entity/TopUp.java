package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 充值实体  *
 * @author yaoshu
 */
@Data
@TableName("top_ups")
public class TopUp {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Long amount;
    private Double money;

    private String tradeNo;

    private String paymentMethod;

    private String paymentProvider;

    private Long createTime;

    private Long completeTime;

    private String status;
}
