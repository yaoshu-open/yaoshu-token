package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 供应商实体  *
 * @author yaoshu
 */
@Data
@TableName("vendors")
public class Vendor {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;
    private String description;
    private String icon;
    private Integer status;

    private Long createdTime;

    private Long updatedTime;

    private Long deletedAt;
}
