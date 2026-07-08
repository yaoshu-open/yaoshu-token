package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 兑换码实体  *
 * @author yaoshu
 */
@Data
@TableName("redemptions")
public class Redemption {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    @TableField("`key`")
    private String key;

    private Integer status;
    private String name;
    private Integer quota;

    private Long createdTime;

    private Long redeemedTime;

    @TableField(exist = false)
    private Integer count;

    private Integer usedUserId;

    private Long deletedAt;

    private Long expiredTime;
}
