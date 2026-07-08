package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 签到实体  *
 * @author yaoshu
 */
@Data
@TableName("checkins")
public class Checkin {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private String checkinDate;

    private Integer quotaAwarded;

    private Long createdAt;
}
