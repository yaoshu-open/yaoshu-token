package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Midjourney 实体  *
 * @author yaoshu
 */
@Data
@TableName("midjourneys")
public class Midjourney {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer code;

    private Integer userId;

    private String action;

    private String mjId;

    private String prompt;

    private String promptEn;

    private String description;
    private String state;

    private Long submitTime;

    private Long startTime;

    private Long finishTime;

    private String imageUrl;

    private String videoUrl;

    private String videoUrls;

    private String status;
    private String progress;

    private String failReason;

    private Integer channelId;

    private Integer quota;
    private String buttons;
    private String properties;
}
