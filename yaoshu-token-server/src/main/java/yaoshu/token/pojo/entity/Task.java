package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 任务实体  *
 * @author yaoshu
 */
@Data
@TableName("tasks")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long createdAt;

    private Long updatedAt;

    private String taskId;

    private String platform;

    private Integer userId;

    @TableField("`group`")
    private String group;

    private Integer channelId;

    private Integer quota;
    private String action;
    private String status;

    private String failReason;

    private Long submitTime;

    private Long startTime;

    private Long finishTime;

    private String progress;
    private String properties;

    private String privateData;

    private String data;
}
