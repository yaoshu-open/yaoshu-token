package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 能力实体  *
 * @author yaoshu
 */
@Data
@TableName("abilities")
public class Ability {

    @TableField("`group`")
    private String group;

    private String model;

    private Integer channelId;

    private Boolean enabled;
    private Long priority;
    private Integer weight;

    private String tag;
}
