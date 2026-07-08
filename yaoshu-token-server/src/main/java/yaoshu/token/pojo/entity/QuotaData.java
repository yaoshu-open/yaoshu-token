package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 配额数据实体  *
 * @author yaoshu
 */
@Data
@TableName("quota_data")
public class QuotaData {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private String username;

    private String modelName;

    private Long createdAt;

    private Integer tokenUsed;

    private Integer count;
    private Integer quota;
}
