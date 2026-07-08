package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户实体  *
 * @author yaoshu
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String password;

    @TableField(exist = false)
    private String originalPassword;

    private String displayName;

    private Integer role;
    private Integer status;
    private String email;

    private String githubId;

    private String discordId;

    private String oidcId;

    private String wechatId;

    private String telegramId;

    @TableField(exist = false)
    private String verificationCode;

    private String accessToken;

    private Long quota;

    private Long usedQuota;

    private Integer requestCount;

    @TableField("`group`")
    private String group;

    private String affCode;

    private Integer affCount;

    private Integer affQuota;

    // affHistoryQuota 驼峰转下划线为 aff_history_quota，与 DB 列 aff_history 不一致，需显式映射
    @TableField("aff_history")
    private Integer affHistoryQuota;

    private Integer inviterId;

    private Long deletedAt;

    private String linuxDoId;

    private String setting;
    private String remark;

    private String stripeCustomer;

    private Long createdAt;

    private Long lastLoginAt;
}
