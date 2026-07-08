package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 2FA 设置实体  *
 * @author yaoshu
 */
@Data
@TableName("two_fas")
public class TwoFa {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private String secret;

    private Boolean isEnabled;

    private Integer failedAttempts;

    private Long lockedUntil;

    private Long lastUsedAt;

    private Long createdAt;

    private Long updatedAt;

    private Long deletedAt;
}
