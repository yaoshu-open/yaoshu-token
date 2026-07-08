package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 模型元数据实体  *
 * @author yaoshu
 */
@Data
@TableName("models")
public class ModelMeta {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String modelName;

    private String description;
    private String icon;
    private String tags;

    private Integer vendorId;

    private String endpoints;
    private Integer status;

    private Integer syncOfficial;

    private Long createdTime;

    private Long updatedTime;

    private Long deletedAt;

    @TableField(exist = false)
    private String boundChannels;

    @TableField(exist = false)
    private String enableGroups;

    @TableField(exist = false)
    private String quotaTypes;

    private Integer nameRule;

    /** 最大上下文窗口token数（可空，null=未设置） */
    private Integer maxContext;

    @TableField(exist = false)
    private String matchedModels;

    @TableField(exist = false)
    private Integer matchedCount;
}
