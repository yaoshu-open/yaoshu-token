package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Token 实体  *
 * @author yaoshu
 */
@Data
@TableName("tokens")
public class Token {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    @TableField("`key`")
    private String key;

    private Integer status;
    private String name;

    private Long createdTime;

    private Long accessedTime;

    private Long expiredTime;

    private Long remainQuota;

    private Boolean unlimitedQuota;

    private Boolean modelLimitsEnabled;

    private String modelLimits;

    private String allowIps;

    private Long usedQuota;

    @TableField("`group`")
    private String group;

    private Boolean crossGroupRetry;

    private Long deletedAt;
}
