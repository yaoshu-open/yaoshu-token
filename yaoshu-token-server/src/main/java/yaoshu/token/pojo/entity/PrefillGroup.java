package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 预填分组实体  *
 * @author yaoshu
 */
@Data
@TableName("prefill_groups")
public class PrefillGroup {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;
    private String type;
    private String items;
    private String description;

    private Long createdTime;

    private Long updatedTime;

    private Long deletedAt;
}
